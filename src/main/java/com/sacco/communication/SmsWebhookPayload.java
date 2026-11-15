package com.sacco.communication;

import com.sacco.communication.exception.SmsValidationException;

import java.util.Map;

/**
 * Provider callback fields plus the raw payload retained for support/audit.
 * Africa's Talking uses form-encoded callbacks by default, but some
 * integrations and proxies send JSON, so both are normalised into this shape.
 */
public record SmsWebhookPayload(Map<String, String> fields, String rawPayload) {

    public SmsWebhookPayload {
        fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    /**
     * Returns the first non-blank value for any of the supplied aliases.
     * Aliases make the parser tolerant of AT dashboard/version differences
     * (for example {@code id} versus {@code messageId}).
     */
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
            throw new SmsValidationException(
                    "Missing required webhook field: " + (names.length == 0 ? "unknown" : names[0]));
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
