package com.sacco.communication;

import com.sacco.communication.dto.SmsRecipientResult;
import com.sacco.communication.entity.SmsMessage;
import com.sacco.communication.exception.SmsValidationException;
import com.sacco.communication.repository.SmsMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * All SMS state transitions live here so they run in short, explicit
 * transactions. Network calls never happen inside these methods.
 */
@Service
@RequiredArgsConstructor
public class SmsMessageWriter {

    private final SmsMessageRepository repository;
    private final AfricasTalkingProperties properties;

    @Transactional
    public SmsMessage create(String recipient,
                             String body,
                             SmsMessage.SmsMessageType type,
                             String senderId,
                             String batchId,
                             Long memberId,
                             Long createdBy) {
        return repository.save(build(recipient, body, type, senderId, batchId, memberId, createdBy));
    }

    /**
     * Creates every row of a bulk request in one transaction. Without this,
     * a failure halfway through would leave a partially committed batch that
     * the caller never sees but the dispatcher still sends.
     */
    @Transactional
    public List<SmsMessage> createBatch(List<String> recipients,
                                        String body,
                                        SmsMessage.SmsMessageType type,
                                        String senderId,
                                        String batchId,
                                        Long createdBy) {
        List<SmsMessage> messages = recipients.stream()
                .map(recipient -> build(
                        recipient, body, type, senderId, batchId, null, createdBy))
                .toList();
        return repository.saveAll(messages);
    }

    private SmsMessage build(String recipient,
                             String body,
                             SmsMessage.SmsMessageType type,
                             String senderId,
                             String batchId,
                             Long memberId,
                             Long createdBy) {
        return SmsMessage.builder()
                .reference(UUID.randomUUID().toString())
                .batchId(batchId)
                .recipient(recipient)
                .senderId(senderId)
                .body(body)
                .messageType(type)
                .status(SmsMessage.SmsStatus.QUEUED)
                .attempts(0)
                .maxAttempts(properties.getDispatch().getMaxAttempts())
                .nextAttemptAt(LocalDateTime.now())
                .memberId(memberId)
                .createdBy(createdBy)
                .build();
    }

    /** Claims one QUEUED row for an immediate send. */
    @Transactional
    public boolean beginAttempt(Long messageId) {
        return repository.beginAttempt(messageId,
                SmsMessage.SmsStatus.QUEUED,
                SmsMessage.SmsStatus.SENDING,
                LocalDateTime.now()) == 1;
    }

    /** Claims a set of QUEUED rows for one synchronous bulk call. */
    @Transactional
    public List<Long> beginBulkAttempt(List<Long> messageIds) {
        List<Long> claimed = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Long messageId : messageIds) {
            if (repository.beginAttempt(messageId,
                    SmsMessage.SmsStatus.QUEUED,
                    SmsMessage.SmsStatus.SENDING,
                    now) == 1) {
                claimed.add(messageId);
            }
        }
        return claimed;
    }

    @Transactional
    public SmsMessage markSent(Long messageId, SmsRecipientResult result) {
        SmsMessage message = require(messageId);
        LocalDateTime now = LocalDateTime.now();
        // A delivery report may win the race; never downgrade DELIVERED.
        if (message.getStatus() != SmsMessage.SmsStatus.DELIVERED) {
            message.setStatus(SmsMessage.SmsStatus.SENT);
        }
        message.setProviderMessageId(result.messageId());
        message.setProviderStatus(result.status());
        message.setProviderStatusCode(result.statusCode());
        message.setProviderCost(result.cost());
        message.setFailureReason(null);
        message.setAttempts(message.getAttempts() + 1);
        message.setLastAttemptAt(now);
        message.setSentAt(now);
        message.setNextAttemptAt(now);
        return repository.save(message);
    }

    @Transactional
    public SmsMessage markFailed(Long messageId,
                                 String reason,
                                 Integer statusCode,
                                 String providerStatus) {
        SmsMessage message = require(messageId);
        LocalDateTime now = LocalDateTime.now();
        if (message.getStatus() != SmsMessage.SmsStatus.DELIVERED) {
            message.setStatus(SmsMessage.SmsStatus.FAILED);
        }
        message.setFailureReason(truncate(reason, 500));
        message.setProviderStatusCode(statusCode);
        message.setProviderStatus(providerStatus);
        message.setAttempts(message.getAttempts() + 1);
        message.setLastAttemptAt(now);
        message.setNextAttemptAt(now);
        return repository.save(message);
    }

    @Transactional
    public SmsMessage scheduleRetry(Long messageId,
                                    String reason,
                                    Integer statusCode,
                                    String providerStatus) {
        SmsMessage message = require(messageId);

        // Never requeue a row that another path already finished. This is the
        // key duplicate-suppression guard when one chunk partially succeeds and
        // a later DB write in the same chunk throws.
        if (message.getStatus() != SmsMessage.SmsStatus.QUEUED
                && message.getStatus() != SmsMessage.SmsStatus.SENDING) {
            return message;
        }

        LocalDateTime now = LocalDateTime.now();
        int attempts = message.getAttempts() + 1;
        message.setAttempts(attempts);
        message.setLastAttemptAt(now);
        message.setProviderStatusCode(statusCode);
        message.setProviderStatus(providerStatus);
        message.setFailureReason(truncate(reason, 500));

        if (attempts >= message.getMaxAttempts()) {
            message.setStatus(SmsMessage.SmsStatus.FAILED);
            message.setNextAttemptAt(now);
        } else {
            message.setStatus(SmsMessage.SmsStatus.QUEUED);
            message.setNextAttemptAt(now.plusNanos(backoffMillis(attempts) * 1_000_000L));
        }
        return repository.save(message);
    }

    @Transactional
    public SmsMessage markRetried(Long messageId) {
        SmsMessage message = require(messageId);
        message.setStatus(SmsMessage.SmsStatus.QUEUED);
        message.setAttempts(0);
        message.setFailureReason(null);
        message.setNextAttemptAt(LocalDateTime.now());
        // The retry produces a new provider message id. Clearing the previous
        // id prevents a late delivery report for the old send from being
        // applied to the retried row.
        message.setProviderMessageId(null);
        message.setProviderStatusCode(null);
        message.setProviderStatus(null);
        message.setProviderCost(null);
        message.setSentAt(null);
        message.setDeliveredAt(null);
        return repository.save(message);
    }

    /**
     * Exponential backoff with ±20% jitter. Attempt 1 waits ~30s, attempt 2
     * ~60s, attempt 3 ~2m, capped by the configured maximum (default 1h).
     */
    long backoffMillis(int attempts) {
        long base = Math.max(1000, properties.getDispatch().getBaseBackoffMs());
        long max = Math.max(base, properties.getDispatch().getMaxBackoffMs());
        int exponent = Math.min(Math.max(0, attempts - 1), 20);
        long delay = Math.min(max, base * (1L << exponent));
        double jitter = 0.8 + (ThreadLocalRandom.current().nextDouble() * 0.4);
        return Math.max(1000, Math.min(max, (long) (delay * jitter)));
    }

    private SmsMessage require(Long messageId) {
        return repository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "SMS message not found: " + messageId));
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
