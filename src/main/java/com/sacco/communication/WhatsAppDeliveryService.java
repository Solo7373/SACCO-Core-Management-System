package com.sacco.communication;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.communication.dto.MetaSendResult;
import com.sacco.communication.entity.WhatsAppMessage;
import com.sacco.communication.exception.WhatsAppException;
import com.sacco.communication.exception.WhatsAppValidationException;
import com.sacco.communication.repository.WhatsAppConsentRepository;
import com.sacco.communication.repository.WhatsAppMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Performs Meta API calls and translates the outcome into outbox state
 * transitions. Retryable failures are re-queued with exponential backoff;
 * permanent failures are marked FAILED for staff review.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppDeliveryService {

    private final WhatsAppMessageRepository repository;
    private final WhatsAppMessageWriter writer;
    private final MetaWhatsAppClient client;
    private final WhatsAppConsentRepository consents;
    private final ObjectMapper objectMapper;

    /** Immediate single send: atomically claims the row, then calls Meta. */
    public void deliver(Long messageId) {
        if (!writer.beginAttempt(messageId)) {
            log.debug("WhatsApp {} was already claimed by another worker", messageId);
            return;
        }
        deliverClaimed(messageId);
    }

    /** Sends a row already claimed by the outbox dispatcher (status SENDING). */
    public void deliverClaimed(Long messageId) {
        WhatsAppMessage message = repository.findById(messageId).orElse(null);
        if (message == null || message.getStatus() != WhatsAppMessage.WhatsAppStatus.SENDING) {
            return;
        }

        try {
            MetaSendResult result = send(message);
            writer.markSent(messageId, result);
            consents.findByPhoneNumber(message.getRecipient()).ifPresent(consent -> {
                consent.setLastOutboundAt(LocalDateTime.now());
                consents.save(consent);
            });
        } catch (WhatsAppException exception) {
            if (exception.isRetryable()) {
                writer.scheduleRetry(messageId, exception);
            } else {
                writer.markFailed(messageId, exception);
            }
        } catch (Exception exception) {
            log.error("Unexpected WhatsApp delivery error for message {}", messageId, exception);
            writer.scheduleRetry(messageId, new WhatsAppException(
                    "Unexpected delivery error: " + exception.getMessage(), true, exception));
        }
    }

    private MetaSendResult send(WhatsAppMessage message) {
        return switch (message.getMessageType()) {
            case TEXT -> client.sendText(
                    message.getRecipient(),
                    message.getBody(),
                    message.getReference(),
                    message.getContextMessageId());
            case TEMPLATE -> client.sendTemplate(
                    message.getRecipient(),
                    message.getTemplateName(),
                    message.getTemplateLanguage(),
                    parseParameters(message.getTemplateParameters()),
                    message.getReference());
            case IMAGE, DOCUMENT, AUDIO, VIDEO, STICKER -> client.sendMedia(
                    message.getRecipient(),
                    message.getMessageType().name().toLowerCase(Locale.ROOT),
                    message.getMediaUrl(),
                    message.getMediaId(),
                    message.getCaption(),
                    message.getReference());
            default -> throw new WhatsAppValidationException(
                    "Unsupported WhatsApp message type: " + message.getMessageType());
        };
    }

    private List<String> parseParameters(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception exception) {
            throw new WhatsAppValidationException("Stored template parameters are invalid.");
        }
    }
}
