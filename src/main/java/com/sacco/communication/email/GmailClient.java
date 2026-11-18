package com.sacco.communication.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.storage.google.GmailSettings;
import com.sacco.storage.google.GoogleOAuthTokenService;
import com.sacco.storage.google.GoogleTokenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Minimal Gmail API client built on the JDK HTTP client.
 *
 * <p>Only {@code gmail.send} and profile access are used. The OAuth refresh
 * token is shared with Google Drive backups, so the Google account selected
 * for backups is also the sender of member email. A service account has no
 * mailbox and is therefore rejected with a clear configuration error.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GmailClient {

    private static final String DEFAULT_BASE_URL = "https://gmail.googleapis.com";
    private static final String MOCK_TOKEN = "mock-access-token";
    private static final int MAX_SUBJECT_LENGTH = 200;
    private static final int MAX_BODY_LENGTH = 100_000;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+$");

    private final ObjectMapper objectMapper;
    private final GoogleOAuthTokenService tokenService;

    public GmailConnectionResult testConnection(GmailSettings settings) {
        try {
            String email = requireEmail(settings.fromAddress(), "Gmail sender address");
            String url = normalizeBaseUrl(settings.baseUrl()) + "/gmail/v1/users/me/profile";
            HttpResponse<String> response = sendGet(url, accessToken(settings), timeout(settings));
            if (!isSuccessful(response.statusCode())) {
                return new GmailConnectionResult(false,
                        "Gmail returned HTTP " + response.statusCode() + ": " + truncate(response.body()),
                        null, 0);
            }
            JsonNode profile = objectMapper.readTree(response.body());
            String account = profile.path("emailAddress").asText(email);
            return new GmailConnectionResult(true,
                    "Connected to Gmail as " + account + ".",
                    account,
                    profile.path("messagesTotal").asLong(0));
        } catch (Exception ex) {
            log.warn("Gmail connection test failed: {}", ex.getMessage());
            return new GmailConnectionResult(false, ex.getMessage(), null, 0);
        }
    }

    public GmailSendResult send(GmailSettings settings,
                                String recipient,
                                String subject,
                                String body) {
        String to = requireEmail(recipient, "Recipient email address");
        String from = requireEmail(settings.fromAddress(), "Gmail sender address");
        String safeSubject = requireSubject(subject);
        String safeBody = requireBody(body);

        try {
            String raw = buildRawMessage(settings, from, to, safeSubject, safeBody);
            String payload = objectMapper.writeValueAsString(Map.of("raw", raw));
            String url = normalizeBaseUrl(settings.baseUrl()) + "/gmail/v1/users/me/messages/send";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeout(settings)))
                    .header("Authorization", "Bearer " + accessToken(settings))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient(settings)
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (!isSuccessful(response.statusCode())) {
                throw new EmailDeliveryException("Gmail rejected the message: HTTP "
                        + response.statusCode() + " " + truncate(response.body()));
            }
            JsonNode result = objectMapper.readTree(response.body());
            return new GmailSendResult(
                    result.path("id").asText(),
                    result.path("threadId").asText(null),
                    to,
                    from,
                    safeSubject);
        } catch (EmailException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new EmailDeliveryException("Could not send the email through Gmail: "
                    + ex.getMessage(), ex);
        }
    }

    private String accessToken(GmailSettings settings) {
        String mode = normalizeMode(settings.authMode());
        return switch (mode) {
            case "MOCK" -> MOCK_TOKEN;
            case "OAUTH" -> oauthToken(settings);
            default -> throw new EmailNotConfiguredException(
                    "Gmail sending requires a Google OAuth connection. A service account "
                            + "has no mailbox. Choose OAuth in Storage setup and sign in with the "
                            + "Google account that owns the backup folder.");
        };
    }

    private String oauthToken(GmailSettings settings) {
        try {
            return tokenService.accessToken(
                    settings.credentialsPath(), settings.tokenUri(), timeout(settings));
        } catch (GoogleTokenException ex) {
            throw new EmailNotConfiguredException(
                    "Could not obtain a Gmail access token. Reconnect the Google account in "
                            + "Storage setup. " + ex.getMessage());
        }
    }

    private String buildRawMessage(GmailSettings settings,
                                   String from,
                                   String recipient,
                                   String subject,
                                   String body) {
        StringBuilder mime = new StringBuilder();
        mime.append("From: ").append(formatAddress(settings.fromName(), from)).append("\r\n");
        mime.append("To: ").append(recipient).append("\r\n");
        if (settings.replyTo() != null && !settings.replyTo().isBlank()) {
            String replyTo = requireEmail(settings.replyTo(), "Reply-to address");
            mime.append("Reply-To: ").append(replyTo).append("\r\n");
        }
        mime.append("Subject: ").append(encodeSubject(subject)).append("\r\n");
        mime.append("Date: ").append(DateTimeFormatter.RFC_1123_DATE_TIME
                .format(ZonedDateTime.now(ZoneOffset.UTC))).append("\r\n");
        mime.append("Message-ID: <").append(UUID.randomUUID()).append("@sacco.local>\r\n");
        mime.append("MIME-Version: 1.0\r\n");
        mime.append("Content-Type: text/plain; charset=UTF-8\r\n");
        mime.append("Content-Transfer-Encoding: base64\r\n");
        mime.append("\r\n");
        mime.append(Base64.getMimeEncoder(76, "\r\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(body.getBytes(StandardCharsets.UTF_8)));
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mime.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String formatAddress(String name, String email) {
        String display = sanitizeHeaderValue(name);
        if (display.isBlank()) {
            return email;
        }
        String quoted = display.replace("\\", "").replace("\"", "");
        return "\"" + quoted + "\" <" + email + ">";
    }

    private String encodeSubject(String subject) {
        String sanitized = sanitizeHeaderValue(subject);
        boolean ascii = sanitized.chars().allMatch(value -> value >= 32 && value < 127);
        if (ascii) {
            return sanitized;
        }
        String encoded = Base64.getEncoder()
                .encodeToString(sanitized.getBytes(StandardCharsets.UTF_8));
        return "=?UTF-8?B?" + encoded + "?=";
    }

    private String sanitizeHeaderValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", " ").replace("\n", " ").trim();
    }

    private String requireEmail(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new EmailValidationException(label + " is required.");
        }
        if (normalized.length() > 320
                || normalized.indexOf('\r') >= 0
                || normalized.indexOf('\n') >= 0
                || !EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new EmailValidationException(label + " is not a valid email address.");
        }
        return normalized;
    }

    private String requireSubject(String value) {
        if (value != null && (value.contains("\r") || value.contains("\n"))) {
            throw new EmailValidationException("Email subject must not contain line breaks.");
        }
        String normalized = sanitizeHeaderValue(value);
        if (normalized.isEmpty()) {
            throw new EmailValidationException("Email subject is required.");
        }
        if (normalized.length() > MAX_SUBJECT_LENGTH) {
            throw new EmailValidationException(
                    "Email subject must be " + MAX_SUBJECT_LENGTH + " characters or fewer.");
        }
        return normalized;
    }

    private String requireBody(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new EmailValidationException("Email message body is required.");
        }
        if (normalized.length() > MAX_BODY_LENGTH) {
            throw new EmailValidationException(
                    "Email message body must be " + MAX_BODY_LENGTH + " characters or fewer.");
        }
        return normalized;
    }

    private HttpResponse<String> sendGet(String url, String token, int timeoutMs)
            throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return httpClient(timeoutMs).send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpClient httpClient(GmailSettings settings) {
        return httpClient(timeout(settings));
    }

    private HttpClient httpClient(int timeoutMs) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(Math.max(timeoutMs, 1000), 10000)))
                .build();
    }

    private int timeout(GmailSettings settings) {
        return settings.timeoutMs() <= 0 ? 30000 : settings.timeoutMs();
    }

    private String normalizeBaseUrl(String value) {
        String base = value == null || value.isBlank() ? DEFAULT_BASE_URL : value.trim();
        return base.replaceAll("/+$", "");
    }

    private String normalizeMode(String value) {
        return value == null || value.isBlank()
                ? "SERVICE_ACCOUNT"
                : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private boolean isSuccessful(int status) {
        return status >= 200 && status < 300;
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "...";
    }
}
