package com.sacco.communication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.audit.AuditService;
import com.sacco.communication.entity.WhatsAppConsent;
import com.sacco.communication.entity.WhatsAppInboundMessage;
import com.sacco.communication.entity.WhatsAppMessage;
import com.sacco.communication.entity.WhatsAppWebhookEvent;
import com.sacco.communication.repository.WhatsAppConsentRepository;
import com.sacco.communication.repository.WhatsAppInboundMessageRepository;
import com.sacco.communication.repository.WhatsAppMessageRepository;
import com.sacco.communication.repository.WhatsAppWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Applies Meta callbacks to the outbox, inbound history and consent state.
 *
 * <p>Every status is stored first. If it wins the race against the send call
 * (the outbox row does not yet have a Meta message id), it stays unprocessed
 * and the dispatcher reconciles it on a later tick. This keeps the webhook
 * idempotent and avoids losing early delivery receipts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppWebhookService {

    private static final Set<String> STOP_KEYWORDS =
            Set.of("STOP", "STOPALL", "UNSUBSCRIBE", "CANCEL", "END", "QUIT");
    private static final Set<String> START_KEYWORDS =
            Set.of("START", "UNSTOP", "SUBSCRIBE");

    private final WhatsAppWebhookEventRepository events;
    private final WhatsAppInboundMessageRepository inboundMessages;
    private final WhatsAppMessageRepository messages;
    private final WhatsAppConsentRepository consents;
    private final PhoneNumberService phoneNumbers;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void process(WhatsAppWebhookPayload payload) {
        for (WhatsAppWebhookPayload.StatusUpdate status : payload.statuses()) {
            processStatus(status);
        }
        for (WhatsAppWebhookPayload.InboundMessage message : payload.messages()) {
            processInbound(message, payload.rawPayload());
        }
    }

    /**
     * Reconciles status events that arrived before their outbox row had a Meta
     * message id. Old unmatched events are closed out for audit retention.
     */
    @Transactional
    public int reprocessPendingEvents() {
        LocalDateTime now = LocalDateTime.now();
        List<WhatsAppWebhookEvent> pending = events
                .findTop100ByProcessedFalseAndReceivedAtAfterOrderByEventIdAsc(
                        now.minusHours(24));

        int matched = 0;
        for (WhatsAppWebhookEvent event : pending) {
            if (!"STATUS".equals(event.getEventType())) {
                continue;
            }
            try {
                WhatsAppWebhookPayload.StatusUpdate status = objectMapper.readValue(
                        event.getPayload(), WhatsAppWebhookPayload.StatusUpdate.class);
                if (applyStatus(status)) {
                    event.setProcessed(true);
                    event.setProcessedAt(now);
                    events.save(event);
                    matched++;
                }
            } catch (Exception exception) {
                log.warn("Could not reprocess WhatsApp event {}: {}",
                        event.getEventId(), exception.getMessage());
            }
        }

        events.markOldEventsProcessed(now.minusHours(24), now);
        return matched;
    }

    private void processStatus(WhatsAppWebhookPayload.StatusUpdate status) {
        if (blank(status.providerMessageId())) {
            log.warn("Ignoring WhatsApp status callback without a message id");
            return;
        }
        String eventKey = status.eventKey();
        if (events.findByEventKey(eventKey).isPresent()) {
            return;
        }

        WhatsAppWebhookEvent event = events.save(WhatsAppWebhookEvent.builder()
                .eventKey(truncate(eventKey, 200))
                .eventType("STATUS")
                .providerMessageId(truncate(status.providerMessageId(), 128))
                .payload(toJson(status))
                .processed(false)
                .receivedAt(LocalDateTime.now())
                .build());

        if (applyStatus(status)) {
            event.setProcessed(true);
            event.setProcessedAt(LocalDateTime.now());
            events.save(event);
        }
    }

    private boolean applyStatus(WhatsAppWebhookPayload.StatusUpdate status) {
        Optional<WhatsAppMessage> found = messages
                .findFirstByProviderMessageIdOrderByMessageIdAsc(status.providerMessageId());
        if (found.isEmpty()) {
            // The send call has not stored the provider id yet. Leave the event
            // unprocessed for the dispatcher to retry.
            return false;
        }

        WhatsAppMessage message = found.get();
        String raw = status.status() == null
                ? ""
                : status.status().trim().toLowerCase(Locale.ROOT);
        LocalDateTime now = LocalDateTime.now();

        switch (raw) {
            case "sent" -> {
                if (message.getStatus() == WhatsAppMessage.WhatsAppStatus.QUEUED
                        || message.getStatus() == WhatsAppMessage.WhatsAppStatus.SENDING) {
                    message.setStatus(WhatsAppMessage.WhatsAppStatus.SENT);
                    message.setSentAt(message.getSentAt() == null ? now : message.getSentAt());
                    message.setProviderStatus("sent");
                }
            }
            case "delivered" -> {
                if (message.getStatus() != WhatsAppMessage.WhatsAppStatus.READ) {
                    message.setStatus(WhatsAppMessage.WhatsAppStatus.DELIVERED);
                    message.setDeliveredAt(message.getDeliveredAt() == null
                            ? now : message.getDeliveredAt());
                    message.setSentAt(message.getSentAt() == null ? now : message.getSentAt());
                    message.setProviderStatus("delivered");
                    clearErrors(message);
                }
            }
            case "read" -> {
                message.setStatus(WhatsAppMessage.WhatsAppStatus.READ);
                message.setReadAt(message.getReadAt() == null ? now : message.getReadAt());
                message.setDeliveredAt(message.getDeliveredAt() == null
                        ? now : message.getDeliveredAt());
                message.setSentAt(message.getSentAt() == null ? now : message.getSentAt());
                message.setProviderStatus("read");
                clearErrors(message);
            }
            case "failed" -> {
                // A late failure must never overwrite a confirmed delivery/read.
                if (message.getStatus() != WhatsAppMessage.WhatsAppStatus.DELIVERED
                        && message.getStatus() != WhatsAppMessage.WhatsAppStatus.READ) {
                    message.setStatus(WhatsAppMessage.WhatsAppStatus.FAILED);
                    message.setFailedAt(message.getFailedAt() == null ? now : message.getFailedAt());
                    message.setProviderStatus("failed");
                    applyErrors(message, status);
                }
            }
            default -> {
                message.setProviderStatus(truncate(raw, 40));
                log.warn("Unrecognised WhatsApp status '{}' for message {}",
                        status.status(), message.getMessageId());
            }
        }

        messages.save(message);
        return true;
    }

    private void processInbound(WhatsAppWebhookPayload.InboundMessage message,
                                String rawPayload) {
        if (blank(message.providerMessageId())) {
            log.warn("Ignoring WhatsApp inbound callback without a message id");
            return;
        }
        String eventKey = message.eventKey();
        if (events.findByEventKey(eventKey).isPresent()) {
            return;
        }

        WhatsAppWebhookEvent event = events.save(WhatsAppWebhookEvent.builder()
                .eventKey(truncate(eventKey, 200))
                .eventType("MESSAGE")
                .providerMessageId(truncate(message.providerMessageId(), 128))
                .payload(toJson(message))
                .processed(false)
                .receivedAt(LocalDateTime.now())
                .build());

        if (inboundMessages.findByProviderMessageId(message.providerMessageId()).isPresent()) {
            markProcessed(event);
            return;
        }

        String phone = storePhone(message.from());
        if (phone == null) {
            markProcessed(event);
            return;
        }

        LocalDateTime receivedAt = message.timestamp() == null
                ? LocalDateTime.now()
                : message.timestamp();

        WhatsAppInboundMessage inbound = inboundMessages.save(
                WhatsAppInboundMessage.builder()
                        .providerMessageId(truncate(message.providerMessageId(), 128))
                        .fromNumber(phone)
                        .contactName(truncate(message.contactName(), 255))
                        .messageType(truncate(message.messageType(), 30))
                        .body(message.body())
                        .mediaId(truncate(message.mediaId(), 128))
                        .mediaMimeType(truncate(message.mediaMimeType(), 100))
                        .contextMessageId(truncate(message.contextMessageId(), 128))
                        .receivedAt(receivedAt)
                        .rawPayload(rawPayload)
                        .processed(false)
                        .build());

        applyConsent(phone, message.body(), receivedAt);

        inbound.setProcessed(true);
        inboundMessages.save(inbound);
        markProcessed(event);
    }

    private void applyConsent(String phone, String text, LocalDateTime receivedAt) {
        WhatsAppConsent consent = consents.findByPhoneNumber(phone)
                .orElseGet(() -> WhatsAppConsent.builder()
                        .phoneNumber(phone)
                        .status(WhatsAppConsent.ConsentStatus.OPT_IN)
                        .source("INBOUND_MESSAGE")
                        .optedInAt(receivedAt)
                        .build());

        consent.setLastInboundAt(receivedAt);
        if (consent.getStatus() == null) {
            consent.setStatus(WhatsAppConsent.ConsentStatus.OPT_IN);
        }

        if (text != null && !text.isBlank()) {
            String firstToken = text.trim().toUpperCase(Locale.ROOT)
                    .split("\\s+")[0]
                    .replaceAll("[^A-Z]", "");

            if (STOP_KEYWORDS.contains(firstToken)) {
                consent.setStatus(WhatsAppConsent.ConsentStatus.OPT_OUT);
                consent.setOptedOutAt(receivedAt);
                consent.setSource("INBOUND_STOP");
                consent = consents.save(consent);
                audit("WHATSAPP_OPT_OUT", consent);
                return;
            }
            if (START_KEYWORDS.contains(firstToken)) {
                consent.setStatus(WhatsAppConsent.ConsentStatus.OPT_IN);
                consent.setOptedInAt(receivedAt);
                consent.setSource("INBOUND_START");
                consent = consents.save(consent);
                audit("WHATSAPP_OPT_IN", consent);
                return;
            }
        }

        consents.save(consent);
    }

    private void markProcessed(WhatsAppWebhookEvent event) {
        event.setProcessed(true);
        event.setProcessedAt(LocalDateTime.now());
        events.save(event);
    }

    private void applyErrors(WhatsAppMessage message,
                             WhatsAppWebhookPayload.StatusUpdate status) {
        message.setErrorCode(status.errorCode());
        message.setErrorSubcode(status.errorSubcode());
        message.setErrorTitle(truncate(status.errorTitle(), 255));
        String reason = status.errorMessage();
        if (reason == null || reason.isBlank()) {
            reason = status.errorTitle();
        }
        if (reason == null || reason.isBlank()) {
            reason = "Meta reported status failed";
        }
        message.setErrorMessage(truncate(reason, 2000));
        message.setFbtraceId(truncate(status.fbtraceId(), 128));
    }

    private void clearErrors(WhatsAppMessage message) {
        message.setErrorCode(null);
        message.setErrorSubcode(null);
        message.setErrorTitle(null);
        message.setErrorMessage(null);
        message.setFbtraceId(null);
    }

    private void audit(String action, WhatsAppConsent consent) {
        try {
            auditService.logEvent(0L, action, "WHATSAPP_CONSENT",
                    consent.getConsentId(),
                    "Phone " + phoneNumbers.mask(consent.getPhoneNumber()));
        } catch (RuntimeException exception) {
            // An audit write failure must not cause Meta to retry the callback.
            log.error("Could not write WhatsApp consent audit event", exception);
        }
    }

    private String storePhone(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return phoneNumbers.normalize(raw);
        } catch (RuntimeException exception) {
            return truncate(raw.trim(), 20);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            return String.valueOf(value);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
