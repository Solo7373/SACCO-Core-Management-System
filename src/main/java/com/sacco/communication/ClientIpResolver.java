package com.sacco.communication;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves the client address used by the webhook IP allowlist and the OTP
 * rate limiter.
 *
 * <p>Forwarded headers are only honoured when explicitly enabled. A caller can
 * set {@code X-Forwarded-For} to any value, so trusting it without a reverse
 * proxy in front of the application would defeat an IP allowlist.
 */
@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private final AfricasTalkingProperties properties;

    public String resolve(HttpServletRequest request) {
        String candidate = null;

        if (properties.isWebhookTrustForwardedHeaders()) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                // The first entry is the original client; later entries are proxies.
                candidate = forwardedFor.split(",")[0].trim();
            }
            if (candidate == null || candidate.isBlank()) {
                candidate = request.getHeader("X-Real-IP");
            }
        }

        if (candidate == null || candidate.isBlank()) {
            candidate = request.getRemoteAddr();
        }
        return normalize(candidate);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        if (cleaned.length() > 1 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        // Some proxies append an IPv4 port (1.2.3.4:5678).
        if (cleaned.matches("\\d{1,3}(\\.\\d{1,3}){3}:\\d+")) {
            cleaned = cleaned.substring(0, cleaned.lastIndexOf(':'));
        }
        if (cleaned.startsWith("[")) {
            int closing = cleaned.indexOf(']');
            if (closing > 0) {
                cleaned = cleaned.substring(1, closing);
            }
        }
        // Remove an IPv6 zone identifier such as fe80::1%eth0.
        int zone = cleaned.indexOf('%');
        if (zone > 0) {
            cleaned = cleaned.substring(0, zone);
        }
        return cleaned.isBlank() ? null : cleaned;
    }
}
