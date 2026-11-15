package com.sacco.communication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.communication.dto.MetaSendResult;
import com.sacco.communication.entity.WhatsAppMessage;
import com.sacco.communication.exception.WhatsAppException;
import com.sacco.communication.repository.WhatsAppMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * All WhatsApp state transitions live here so they run in short, explicit
 * transactions. Network calls never happen inside these methods.
 */
@Service
@RequiredArgsConstructor
public class WhatsAppMessageWriter {

    private final WhatsAppMessageRepository repository;
    private final WhatsAppProperties properties;
    private final ObjectMapper objectMapper;

    @Transactional
    public WhatsAppMessage createText(String recipient,
                                      String body,
                                      String contextMessageId,
                                      String batchId,
                                      Long memberId,
                                      Long createdBy) {
        WhatsAppMessage message = base(recipient, batchId, memberId, createdBy)
                .messageType(WhatsAppMessage.WhatsAppMessageType.TEXT)
                .body(body)
                .contextMessageId(contextMessageId)
                .build();
        return repository.save(message);
    }

    @Transactional
    public WhatsAppMessage createTemplate(String recipient,
                                          String templateName,
                                          String language,
                                          List<String> parameters,
                                          String batchId,
                                          Long memberId,
                                          Long createdBy) {
        WhatsAppMessage message = base(recipient, batchId, memberId, createdBy)
                .messageType(WhatsAppMessage.WhatsAppMessageType.TEMPLATE)
                .templateName(templateName)
                .templateLanguage(language)
                .templateParameters(toJson(parameters))
                .build();
        return repository.save(message);
    }

    @Transactional
    public WhatsAppMessage createMedia(String recipient,
                                       WhatsAppMessage.WhatsAppMessageType type,
                                       String mediaUrl,
                                       String mediaId,
                                       String caption,
                                       String contextMessageId,
                                       String batchId,
                                       Long memberId,
                                       Long createdBy) {
        WhatsAppMessage message = base(recipient, batchId, memberId, createdBy)
                .messageType(type)
                .mediaUrl(mediaUrl)
                .mediaId(mediaId)
                .caption(caption)
                .contextMessageId(contextMessageId)
                .build();
        return repository.save(message);
    }

    private WhatsAppMessage.WhatsAppMessageBuilder base(String recipient,
                                                        String batchId,
                                                        Long memberId,
                                                        Long createdBy) {
        return WhatsAppMessage.builder()
                .reference(UUID.randomUUID().toString())
                .batchId(batchId)
                .recipient(recipient)
                .status(WhatsAppMessage.WhatsAppStatus.QUEUED)
                .attempts(0)
                .maxAttempts(properties.getDispatch().getMaxAttempts())
                .nextAttemptAt(LocalDateTime.now())
                .memberId(memberId)
                .createdBy(createdBy);
    }

    /** Claims one QUEUED row for an immediate send. */
    @Transactional
    public boolean beginAttempt(Long messageId) {
        return repository.beginAttempt(messageId,
                WhatsAppMessage.WhatsAppStatus.QUEUED,
                WhatsAppMessage.WhatsAppStatus.SENDING,
                LocalDateTime.now()) == 1;
    }

    @Transactional
    public WhatsAppMessage markSent(Long messageId, MetaSendResult result) {
        WhatsAppMessage message = require(messageId);
        LocalDateTime now = LocalDateTime.now();

        // A status callback may win the race; never downgrade a confirmed
        // delivery or read receipt.
        if (rank(message.getStatus()) < rank(WhatsAppMessage.WhatsAppStatus.SENT)) {
            message.setStatus(WhatsAppMessage.WhatsAppStatus.SENT);
        }
        message.setProviderMessageId(result.providerMessageId());
        message.setRecipientWaId(result.recipientWaId());
        message.setProviderStatus(result.providerStatus());
        message.setErrorCode(null);
        message.setErrorSubcode(null);
        message.setErrorTitle(null);
        message.setErrorMessage(null);
        message.setFbtraceId(null);
        message.setAttempts(message.getAttempts() + 1);
        message.setLastAttemptAt(now);
        message.setSentAt(now);
        message.setNextAttemptAt(now);
        return repository.save(message);
    }

    @Transactional
    public WhatsAppMessage markFailed(Long messageId, WhatsAppException exception) {
        return markFailed(messageId,
                exception.getMessage(),
                exception.getCode(),
                exception.getSubcode(),
                exception.getTitle(),
                exception.getFbtraceId());
    }

    @Transactional
    public WhatsAppMessage markFailed(Long messageId,
                                      String reason,
                                      Integer code,
                                      Integer subcode,
                                      String title,
                                      String fbtraceId) {
        WhatsAppMessage message = require(messageId);
        LocalDateTime now = LocalDateTime.now();

        // A late failure must never overwrite a confirmed delivery or read.
        if (message.getStatus() != WhatsAppMessage.WhatsAppStatus.DELIVERED
                && message.getStatus() != WhatsAppMessage.WhatsAppStatus.READ) {
            message.setStatus(WhatsAppMessage.WhatsAppStatus.FAILED);
            message.setFailedAt(now);
        }
        message.setErrorMessage(truncate(reason, 2000));
        message.setErrorCode(code);
        message.setErrorSubcode(subcode);
        message.setErrorTitle(truncate(title, 255));
        message.setFbtraceId(truncate(fbtraceId, 128));
        message.setAttempts(message.getAttempts() + 1);
        message.setLastAttemptAt(now);
        message.setNextAttemptAt(now);
        return repository.save(message);
    }

    @Transactional
    public WhatsAppMessage scheduleRetry(Long messageId, WhatsAppException exception) {
        WhatsAppMessage message = require(messageId);

        if (message.getStatus() != WhatsAppMessage.WhatsAppStatus.QUEUED
                && message.getStatus() != WhatsAppMessage.WhatsAppStatus.SENDING) {
            return message;
        }

        LocalDateTime now = LocalDateTime.now();
        int attempts = message.getAttempts() + 1;
        message.setAttempts(attempts);
        message.setLastAttemptAt(now);
        message.setErrorCode(exception.getCode());
        message.setErrorSubcode(exception.getSubcode());
        message.setErrorTitle(truncate(exception.getTitle(), 255));
        message.setErrorMessage(truncate(exception.getMessage(), 2000));
        message.setFbtraceId(truncate(exception.getFbtraceId(), 128));

        if (attempts >= message.getMaxAttempts()) {
            message.setStatus(WhatsAppMessage.WhatsAppStatus.FAILED);
            message.setFailedAt(now);
            message.setNextAttemptAt(now);
        } else {
            message.setStatus(WhatsAppMessage.WhatsAppStatus.QUEUED);
            message.setNextAttemptAt(now.plusNanos(backoffMillis(attempts) * 1_000_000L));
        }
        return repository.save(message);
    }

    @Transactional
    public WhatsAppMessage markRetried(Long messageId) {
        WhatsAppMessage message = require(messageId);
        message.setStatus(WhatsAppMessage.WhatsAppStatus.QUEUED);
        message.setAttempts(0);
        message.setNextAttemptAt(LocalDateTime.now());
        // The retry produces a new provider message id. Clearing the previous
        // id prevents a late status for the old send from being applied.
        message.setProviderMessageId(null);
        message.setProviderStatus(null);
        message.setRecipientWaId(null);
        message.setErrorCode(null);
        message.setErrorSubcode(null);
        message.setErrorTitle(null);
        message.setErrorMessage(null);
        message.setFbtraceId(null);
        message.setSentAt(null);
        message.setDeliveredAt(null);
        message.setReadAt(null);
        message.setFailedAt(null);
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

    private int rank(WhatsAppMessage.WhatsAppStatus status) {
        if (status == null) {
            return -1;
        }
        return switch (status) {
            case QUEUED -> 0;
            case SENDING -> 1;
            case SENT -> 2;
            case DELIVERED -> 3;
            case READ -> 4;
            case FAILED -> 2;
        };
    }

    private String toJson(List<String> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not serialize template parameters", exception);
        }
    }

    private WhatsAppMessage require(Long messageId) {
        return repository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "WhatsApp message not found: " + messageId));
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
