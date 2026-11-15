package com.sacco.communication;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Normalised Meta webhook content. A single callback can contain multiple
 * status updates and inbound messages, so both are kept as lists.
 */
public record WhatsAppWebhookPayload(
        List<StatusUpdate> statuses,
        List<InboundMessage> messages,
        String rawPayload) {

    public WhatsAppWebhookPayload {
        statuses = statuses == null ? List.of() : List.copyOf(statuses);
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public record StatusUpdate(
            String providerMessageId,
            String status,
            LocalDateTime timestamp,
            String recipientId,
            Integer errorCode,
            Integer errorSubcode,
            String errorTitle,
            String errorMessage,
            String fbtraceId) {

        public String eventKey() {
            return "status:" + providerMessageId + ":" + status + ":"
                    + (timestamp == null ? "" : timestamp);
        }
    }

    public record InboundMessage(
            String providerMessageId,
            String from,
            String contactName,
            String messageType,
            String body,
            String mediaId,
            String mediaMimeType,
            String contextMessageId,
            LocalDateTime timestamp) {

        public String eventKey() {
            return "message:" + providerMessageId;
        }
    }
}
