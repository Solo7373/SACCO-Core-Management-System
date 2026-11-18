package com.sacco.storage.google;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.storage.drive.GoogleDriveCredentialsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Exchanges a stored Google OAuth refresh token for a short-lived access
 * token. Drive backups and Gmail sending share the same refresh token and
 * therefore the same cache entry.
 *
 * <p>The refresh token must be issued with both the Drive scope and the Gmail
 * send scope. Scopes are fixed when the refresh token is created; this
 * service never requests a different scope.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleOAuthTokenService {

    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";

    private final ObjectMapper objectMapper;
    private final GoogleDriveCredentialsService credentialsService;

    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

    public String accessToken(String credentialsPath, String tokenUri, int timeoutMs) {
        String endpoint = tokenUri == null || tokenUri.isBlank()
                ? DEFAULT_TOKEN_URI
                : tokenUri.trim();
        String cacheKey = credentialsPath + "|" + endpoint;
        CachedToken cached = cache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return cached.accessToken();
        }

        JsonNode credentials = credentialsService.readJson(credentialsPath);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("client_id", required(credentials.path("client_id").asText(""),
                "The OAuth credentials file is missing client_id."));
        form.put("client_secret", required(credentials.path("client_secret").asText(""),
                "The OAuth credentials file is missing client_secret."));
        form.put("refresh_token", required(credentials.path("refresh_token").asText(""),
                "The OAuth credentials file is missing refresh_token."));

        TokenResponse token = parseTokenResponse(postForm(endpoint, form, timeoutMs));
        long ttl = Math.max(token.expiresInSeconds() - 60, 60);
        cache.put(cacheKey, new CachedToken(token.accessToken(), Instant.now().plusSeconds(ttl)));
        return token.accessToken();
    }

    /**
     * Drops cached tokens after the stored credentials or token endpoint
     * change. The next request obtains a fresh access token.
     */
    public void clear() {
        cache.clear();
    }

    private String postForm(String tokenUri, Map<String, String> form, int timeoutMs) {
        try {
            String body = form.entrySet().stream()
                    .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                    .collect(Collectors.joining("&"));
            HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUri))
                    .timeout(Duration.ofMillis(timeoutMs <= 0 ? 30000 : timeoutMs))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(Math.min(Math.max(timeoutMs, 1000), 10000)))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GoogleTokenException("Google token endpoint returned HTTP "
                        + response.statusCode() + ": " + truncate(response.body()));
            }
            return response.body();
        } catch (GoogleTokenException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GoogleTokenException("Could not obtain a Google access token.", ex);
        }
    }

    private TokenResponse parseTokenResponse(String body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            String token = json.path("access_token").asText("");
            if (token.isBlank()) {
                String error = json.path("error_description").asText(
                        json.path("error").asText("Unknown token error"));
                throw new GoogleTokenException("Google token request failed: " + error);
            }
            return new TokenResponse(token, json.path("expires_in").asLong(3600));
        } catch (GoogleTokenException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GoogleTokenException("Google returned an unreadable token response.", ex);
        }
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new GoogleTokenException(message);
        }
        return value.trim();
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500) + "…";
    }

    private record TokenResponse(String accessToken, long expiresInSeconds) {
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }
}
