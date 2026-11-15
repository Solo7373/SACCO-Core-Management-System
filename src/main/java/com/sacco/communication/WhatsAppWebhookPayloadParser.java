package com.sacco.communication;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.communication.exception.WhatsAppValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses Meta WhatsApp webhook payloads without trusting their content.
 *
 * <p>Meta sends a nested {@code entry[].changes[].value} structure containing
 * zero or more {@code statuses} and {@code messages}. Raw payloads are bounded
 * to 64 KB before persistence so an unauthenticated caller cannot fill the
 * database with an oversized body.
 */
@Component
@RequiredArgsConstructor
public class WhatsAppWebhookPayloadParser {

    private static final int MAX_RAW_BYTES = 64 * 1024;

    private final ObjectMapper objectMapper;

    public WhatsAppWebhookPayload parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new WhatsAppValidationException("Webhook payload is empty.");
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_RAW_BYTES) {
            throw new WhatsAppValidationException("Webhook payload is too large.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(raw);
        } catch (Exception exception) {
            throw new WhatsAppValidationException("Webhook payload is not valid JSON.");
        }
        if (root == null || !root.isObject()) {
            throw new WhatsAppValidationException("Webhook payload must be a JSON object.");
        }

        // Meta sends other webhook objects to the same app (for example
        // account updates). Acknowledge them without treating them as messages.
        if (!"whatsapp_business_account".equals(root.path("object").asText())) {
            return new WhatsAppWebhookPayload(List.of(), List.of(), truncate(raw));
        }

        List<WhatsAppWebhookPayload.StatusUpdate> statuses = new ArrayList<>();
        List<WhatsAppWebhookPayload.InboundMessage> messages = new ArrayList<>();

        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                JsonNode value = change.path("value");
                String contactName = contactName(value);

                for (JsonNode status : value.path("statuses")) {
                    statuses.add(parseStatus(status));
                }
                for (JsonNode message : value.path("messages")) {
                    messages.add(parseMessage(message, contactName));
                }
            }
        }

        return new WhatsAppWebhookPayload(statuses, messages, truncate(raw));
    }

    private WhatsAppWebhookPayload.StatusUpdate parseStatus(JsonNode status) {
        JsonNode error = firstError(status);
        return new WhatsAppWebhookPayload.StatusUpdate(
                text(status, "id"),
                text(status, "status"),
                timestamp(text(status, "timestamp")),
                text(status, "recipient_id"),
                error == null ? null : integer(error, "code"),
                error == null ? null : integer(error, "error_subcode"),
                error == null ? null : text(error, "title"),
                error == null ? null : text(error, "message"),
                error == null ? null : text(error, "fbtrace_id"));
    }

    private WhatsAppWebhookPayload.InboundMessage parseMessage(JsonNode message,
                                                              String contactName) {
        String type = text(message, "type");
        String body = null;
        String mediaId = null;
        String mediaMimeType = null;

        if (type != null) {
            JsonNode content = message.path(type);
            switch (type) {
                case "text" -> body = text(content, "body");
                case "button" -> body = text(content, "text");
                case "interactive" -> body = firstNonBlank(
                        text(content.path("button_reply"), "title"),
                        text(content.path("list_reply"), "title"));
                case "image", "video", "audio", "document", "sticker" -> {
                    mediaId = text(content, "id");
                    mediaMimeType = text(content, "mime_type");
                    body = firstNonBlank(
                            text(content, "caption"),
                            text(content, "filename"));
                }
                case "location" -> body = firstNonBlank(
                        text(content, "latitude"),
                        text(content, "longitude"));
                case "reaction" -> body = text(content, "emoji");
                default -> body = null;
            }
        }

        return new WhatsAppWebhookPayload.InboundMessage(
                text(message, "id"),
                text(message, "from"),
                contactName,
                type,
                body,
                mediaId,
                mediaMimeType,
                text(message.path("context"), "message_id"),
                timestamp(text(message, "timestamp")));
    }

    private String contactName(JsonNode value) {
        JsonNode contacts = value.path("contacts");
        if (!contacts.isArray() || contacts.isEmpty()) {
            return null;
        }
        return text(contacts.get(0).path("profile"), "name");
    }

    private JsonNode firstError(JsonNode status) {
        JsonNode errors = status.path("errors");
        if (!errors.isArray() || errors.isEmpty()) {
            return null;
        }
        return errors.get(0);
    }

    private LocalDateTime timestamp(String epochSeconds) {
        if (epochSeconds == null || epochSeconds.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(Long.parseLong(epochSeconds.trim())),
                    ZoneId.systemDefault());
        } catch (NumberFormatException exception) {
            return LocalDateTime.now();
        }
    }

    private Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asInt() : null;
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_RAW_BYTES ? value : value.substring(0, MAX_RAW_BYTES);
    }
}
