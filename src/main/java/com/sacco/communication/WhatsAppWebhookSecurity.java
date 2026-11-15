package com.sacco.communication;

import com.sacco.communication.exception.WebhookAuthenticationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Authenticates Meta WhatsApp callbacks.
 *
 * <p>Meta signs every POST with {@code X-Hub-Signature-256}, an HMAC-SHA256 of
 * the raw request body using the app secret. The path token is an additional
 * constant-time check. When any webhook secret is missing the endpoint fails
 * closed: an unconfigured deployment must never accept arbitrary callbacks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WhatsAppWebhookSecurity {

    private final WhatsAppProperties properties;

    public void verifyPathToken(String suppliedToken) {
        if (!properties.isWebhookConfigured()) {
            log.error("WhatsApp webhook secrets are not configured; rejecting callback.");
            throw new WebhookAuthenticationException("Invalid webhook credentials.");
        }
        if (!constantTimeEquals(properties.getWebhookToken(), suppliedToken)) {
            throw new WebhookAuthenticationException("Invalid webhook credentials.");
        }
    }

    /**
     * Handles Meta's GET verification handshake and returns the challenge that
     * must be echoed as plain text.
     */
    public String verifyChallenge(String mode, String suppliedVerifyToken, String challenge) {
        if (!properties.isWebhookConfigured()) {
            log.error("WhatsApp webhook secrets are not configured; rejecting verification.");
            throw new WebhookAuthenticationException("Invalid webhook credentials.");
        }
        if (!"subscribe".equalsIgnoreCase(mode)) {
            throw new WebhookAuthenticationException("Unsupported webhook mode.");
        }
        if (!constantTimeEquals(properties.getVerifyToken(), suppliedVerifyToken)) {
            throw new WebhookAuthenticationException("Invalid webhook verify token.");
        }
        if (challenge == null || challenge.isBlank()) {
            throw new WebhookAuthenticationException("Missing webhook challenge.");
        }
        return challenge;
    }

    public void verifySignature(String rawBody, String signatureHeader) {
        if (!properties.isWebhookConfigured()) {
            log.error("WhatsApp app secret is not configured; rejecting callback.");
            throw new WebhookAuthenticationException("Invalid webhook credentials.");
        }
        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            throw new WebhookAuthenticationException("Missing webhook signature.");
        }

        String supplied = signatureHeader.substring("sha256=".length()).trim();
        String expected = hmacSha256Hex(properties.getAppSecret(), rawBody == null ? "" : rawBody);
        if (!constantTimeEquals(expected, supplied)) {
            throw new WebhookAuthenticationException("Invalid webhook signature.");
        }
    }

    private String hmacSha256Hex(String secret, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC-SHA256 is not available", exception);
        }
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        byte[] expectedDigest = sha256(expected == null ? "" : expected);
        byte[] suppliedDigest = sha256(supplied == null ? "" : supplied);
        return MessageDigest.isEqual(expectedDigest, suppliedDigest);
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
