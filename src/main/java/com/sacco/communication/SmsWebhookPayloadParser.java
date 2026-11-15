package com.sacco.communication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.communication.exception.SmsValidationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses Africa's Talking callbacks without trusting their content type.
 *
 * <p>AT's dashboard sends {@code application/x-www-form-urlencoded} by default.
 * The parser also accepts JSON so the same endpoint works with API gateways,
 * test harnesses and future AT dashboard versions. Raw payloads are bounded
 * to 16 KB before they are persisted, which prevents an unauthenticated caller
 * from filling the database with an oversized body.
 */
@Component
@RequiredArgsConstructor
public class SmsWebhookPayloadParser {

    private static final int MAX_RAW_BYTES = 16 * 1024;

    private final ObjectMapper objectMapper;

    public SmsWebhookPayload parse(HttpServletRequest request) {
        String contentType = request.getContentType();
        boolean json = contentType != null
                && contentType.toLowerCase().startsWith("application/json");

        if (json) {
            return parseJson(request);
        }
        return parseForm(request);
    }

    private SmsWebhookPayload parseJson(HttpServletRequest request) {
        String raw;
        try {
            byte[] body = request.getInputStream().readNBytes(MAX_RAW_BYTES + 1);
            if (body.length > MAX_RAW_BYTES) {
                throw new SmsValidationException("Webhook payload is too large.");
            }
            raw = new String(body, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new SmsValidationException("Could not read the webhook payload.");
        }

        if (raw.isBlank()) {
            throw new SmsValidationException("Webhook payload is empty.");
        }

        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root == null || !root.isObject()) {
                throw new SmsValidationException("Webhook payload must be a JSON object.");
            }
            Map<String, String> fields = new LinkedHashMap<>();
            root.fields().forEachRemaining(entry ->
                    fields.put(entry.getKey(), scalar(entry.getValue())));
            if (fields.isEmpty()) {
                throw new SmsValidationException("Webhook payload is empty.");
            }
            return new SmsWebhookPayload(fields, truncate(raw));
        } catch (JsonProcessingException exception) {
            throw new SmsValidationException("Webhook payload is not valid JSON.");
        }
    }

    private SmsWebhookPayload parseForm(HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (key != null) {
                fields.put(key, values == null || values.length == 0 ? "" : values[0]);
            }
        });
        if (fields.isEmpty()) {
            throw new SmsValidationException("Webhook payload is empty.");
        }

        try {
            // Serialising the form fields preserves the callback in one TEXT
            // column while keeping the individual values easy to inspect.
            return new SmsWebhookPayload(fields, truncate(objectMapper.writeValueAsString(fields)));
        } catch (JsonProcessingException exception) {
            return new SmsWebhookPayload(fields, truncate(fields.toString()));
        }
    }

    private String scalar(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        return node.toString();
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_RAW_BYTES ? value : value.substring(0, MAX_RAW_BYTES);
    }
}
