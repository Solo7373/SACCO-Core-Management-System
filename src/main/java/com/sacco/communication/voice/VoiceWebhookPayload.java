package com.sacco.communication.voice;

import java.util.Map;

/**
 * Africa Talking voice callback fields plus the raw payload retained for
 * support/audit. Africa's Talking uses form-encoded callbacks by default and
 * some deployments/proxies send JSON, so both are normalised here.
 */
public record VoiceWebhookPayload(Map<String, String> fields, String rawPayload) {

    public VoiceWebhookPayload {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    /** Returns the first non-blank value for any of the supplied aliases. */
    public String first(String... names) {
        if (names == null) {
            return null;
        }
        for (String name : names) {
            String value = fields.get(name);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    public String require(String... names) {
        String value = first(names);
        if (value == null) {
            throw new com.sacco.communication.exception.SmsValidationException(
                    "Missing required voice callback field: " + (names.length == 0 ? "unknown" : names[0]));
        }
        return value;
    }

    public Integer integer(String... names) {
        String value = first(names);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
