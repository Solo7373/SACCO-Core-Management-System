package com.sacco.communication.voice;

import com.sacco.communication.AfricasTalkingProperties;
import com.sacco.communication.ClientIpResolver;
import com.sacco.communication.exception.WebhookAuthenticationException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Authenticates Africa Africa's Talking voice callbacks.
 *
 * <p>The token is embedded in the callback path because the provider cannot add
 * custom headers. The endpoint fails closed when no token is configured. An
 * optional IP/CIDR allowlist adds defence in depth; forwarded headers are only
 * trusted when the operator enables the trusted-proxy mode.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceWebhookSecurity {

    private final AfricasTalkingProperties properties;
    private final ClientIpResolver clientIpResolver;

    public void verify(String suppliedToken, HttpServletRequest request) {
        String expectedToken = properties.getWebhookToken();
        if (expectedToken == null || expectedToken.isBlank()) {
            log.error("Africa's Talking voice webhook token is not configured; rejecting callback.");
            throw new WebhookAuthenticationException("Invalid webhook credentials.");
        }
        expectedToken = expectedToken.trim();

        if (!constantTimeEquals(expectedToken, suppliedToken)) {
            throw new WebhookAuthenticationException("Invalid webhook credentials.");
        }

        List<String> allowedIps = properties.getWebhookAllowedIps();
        if (allowedIps == null) {
            return;
        }
        List<String> configuredRanges = allowedIps.stream()
                .filter(range -> range != null && !range.isBlank())
                .toList();
        if (configuredRanges.isEmpty()) {
            return;
        }

        String clientIp = clientIpResolver.resolve(request);
        boolean allowed = configuredRanges.stream().anyMatch(range -> matches(range, clientIp));
        if (!allowed) {
            log.warn("Rejected Africa's Talking voice webhook from a non-allowlisted address: {}", clientIp);
            throw new WebhookAuthenticationException("Webhook source is not allowed.");
        }
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        byte[] expectedDigest = sha256(expected);
        byte[] suppliedDigest = sha256(supplied == null ? "" : supplied);
        return MessageDigest.isEqual(expectedDigest, suppliedDigest);
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private boolean matches(String configuredRange, String clientIp) {
        if (clientIp == null || configuredRange == null || configuredRange.isBlank()) {
            return false;
        }
        try {
            String[] parts = configuredRange.trim().split("/", 2);
            byte[] allowed = addressBytes(parts[0]);
            byte[] actual = addressBytes(clientIp);
            if (allowed == null || actual == null || allowed.length != actual.length) {
                return false;
            }

            int prefix = parts.length == 2
                    ? Integer.parseInt(parts[1].trim())
                    : allowed.length * 8;
            if (prefix < 0 || prefix > allowed.length * 8) {
                return false;
            }

            int wholeBytes = prefix / 8;
            int remainingBits = prefix % 8;
            for (int index = 0; index < wholeBytes; index++) {
                if (allowed[index] != actual[index]) {
                    return false;
                }
            }
            if (remainingBits > 0) {
                int mask = 0xFF << (8 - remainingBits);
                return (allowed[wholeBytes] & mask) == (actual[wholeBytes] & mask);
            }
            return true;
        } catch (RuntimeException exception) {
            log.warn("Ignoring invalid voice webhook IP allowlist entry: {}", configuredRange);
            return false;
        }
    }

    private byte[] addressBytes(String value) {
        String cleaned = value.trim();
        if (cleaned.startsWith("[")) {
            int closing = cleaned.indexOf(']');
            if (closing > 0) {
                cleaned = cleaned.substring(1, closing);
            }
        }
        int zone = cleaned.indexOf('%');
        if (zone > 0) {
            cleaned = cleaned.substring(0, zone);
        }
        if (!cleaned.matches("[0-9a-fA-F:.]+")) {
            return null;
        }

        InetAddress address;
        try {
            address = InetAddress.getByName(cleaned);
        } catch (UnknownHostException exception) {
            return null;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 16
                && bytes[10] == (byte) 0xFF
                && bytes[11] == (byte) 0xFF) {
            byte[] ipv4 = new byte[4];
            System.arraycopy(bytes, 12, ipv4, 0, 4);
            return ipv4;
        }
        return bytes;
    }
}
