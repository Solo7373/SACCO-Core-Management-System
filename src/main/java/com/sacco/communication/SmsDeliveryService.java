package com.sacco.communication;

import com.sacco.communication.dto.SmsRecipientResult;
import com.sacco.communication.entity.SmsMessage;
import com.sacco.communication.exception.AfricasTalkingException;
import com.sacco.communication.repository.SmsMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Performs provider calls and translates the outcome into outbox state
 * transitions. Retryable failures are re-queued with exponential backoff;
 * permanent failures are marked FAILED for staff review.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsDeliveryService {

    private final SmsMessageRepository repository;
    private final SmsMessageWriter writer;
    private final AfricasTalkingClient client;
    private final PhoneNumberService phoneNumbers;

    /** Immediate single send: atomically claims the row, then calls AT. */
    public void deliver(Long messageId) {
        if (!writer.beginAttempt(messageId)) {
            log.debug("SMS {} was already claimed by another worker", messageId);
            return;
        }
        deliverClaimed(messageId);
    }

    /** Sends a row already claimed by the outbox dispatcher (status SENDING). */
    public void deliverClaimed(Long messageId) {
        SmsMessage message = repository.findById(messageId).orElse(null);
        if (message == null || message.getStatus() != SmsMessage.SmsStatus.SENDING) {
            return;
        }
        try {
            List<SmsRecipientResult> results = client.send(
                    message.getRecipient(), message.getBody(), message.getSenderId());
            applyResult(messageId, results.isEmpty() ? null : results.get(0));
        } catch (AfricasTalkingException exception) {
            handleProviderFailure(messageId, exception.getMessage(), exception.isRetryable());
        } catch (Exception exception) {
            log.error("Unexpected SMS delivery error for message {}", messageId, exception);
            writer.scheduleRetry(messageId,
                    "Unexpected delivery error: " + exception.getMessage(), null, null);
        }
    }

    /**
     * Synchronous bulk send used by the optional immediate bulk endpoint. Rows
     * are grouped by identical body/sender and sent in chunks of 100.
     */
    public void deliverBulk(List<Long> messageIds) {
        List<Long> claimed = writer.beginBulkAttempt(messageIds);
        if (claimed.isEmpty()) {
            return;
        }
        List<SmsMessage> messages = repository.findAllById(claimed);
        Map<String, List<SmsMessage>> groups = messages.stream()
                .collect(Collectors.groupingBy(
                        message -> message.getBody() + "\u0000" + safe(message.getSenderId()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (List<SmsMessage> group : groups.values()) {
            for (int start = 0; start < group.size(); start += 100) {
                List<SmsMessage> chunk = group.subList(start, Math.min(start + 100, group.size()));
                sendChunk(chunk);
            }
        }
    }

    private void sendChunk(List<SmsMessage> chunk) {
        List<String> numbers = chunk.stream()
                .map(SmsMessage::getRecipient)
                .distinct()
                .toList();
        try {
            List<SmsRecipientResult> results = client.sendBulk(
                    numbers, chunk.get(0).getBody(), chunk.get(0).getSenderId());

            Map<String, SmsRecipientResult> byNumber = new HashMap<>();
            for (SmsRecipientResult result : results) {
                if (result.number() == null) {
                    continue;
                }
                byNumber.putIfAbsent(normalizeKey(result.number()), result);
            }

            for (SmsMessage message : chunk) {
                SmsRecipientResult result = byNumber.get(message.getRecipient());
                if (result == null) {
                    writer.scheduleRetry(message.getMessageId(),
                            "No provider result for this recipient", null, null);
                } else {
                    applyResult(message.getMessageId(), result);
                }
            }
        } catch (AfricasTalkingException exception) {
            for (SmsMessage message : chunk) {
                handleProviderFailure(message.getMessageId(),
                        exception.getMessage(), exception.isRetryable());
            }
        } catch (Exception exception) {
            log.error("Unexpected bulk SMS delivery error", exception);
            for (SmsMessage message : chunk) {
                writer.scheduleRetry(message.getMessageId(),
                        "Unexpected bulk delivery error: " + exception.getMessage(), null, null);
            }
        }
    }

    private void applyResult(Long messageId, SmsRecipientResult result) {
        if (result == null) {
            writer.scheduleRetry(messageId,
                    "Africa's Talking returned no recipient result", null, null);
        } else if (result.success()) {
            writer.markSent(messageId, result);
        } else if (result.retryable()) {
            writer.scheduleRetry(messageId,
                    result.failureReason(), result.statusCode(), result.status());
        } else {
            writer.markFailed(messageId,
                    result.failureReason(), result.statusCode(), result.status());
        }
    }

    private void handleProviderFailure(Long messageId, String reason, boolean retryable) {
        if (retryable) {
            writer.scheduleRetry(messageId, reason, null, null);
        } else {
            writer.markFailed(messageId, reason, null, null);
        }
    }

    private String normalizeKey(String number) {
        try {
            return phoneNumbers.normalize(number);
        } catch (RuntimeException exception) {
            return number;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
