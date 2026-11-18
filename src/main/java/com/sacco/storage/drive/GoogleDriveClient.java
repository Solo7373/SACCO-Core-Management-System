package com.sacco.storage.drive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.storage.google.GoogleOAuthTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Minimal Google Drive v3 client built on the JDK HTTP client.
 *
 * <p>It supports a service-account JSON key, an OAuth refresh token and a
 * local mock mode. Keeping the implementation dependency-free makes the
 * desktop fat jar smaller and lets the same code run against a local mock
 * server in tests.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleDriveClient {

    private static final String DEFAULT_BASE_URL = "https://www.googleapis.com";
    private static final String DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";
    private static final String DRIVE_SCOPE = "https://www.googleapis.com/auth/drive";
    private static final String MOCK_TOKEN = "mock-access-token";

    private final ObjectMapper objectMapper;
    private final GoogleDriveCredentialsService credentialsService;
    private final GoogleOAuthTokenService oauthTokenService;

    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();

    public DriveConnectionResult testConnection(GoogleDriveSettings settings) {
        try {
            String token = accessToken(settings);
            String folderId = required(settings.folderId(), "Google Drive folder ID is required.");
            String url = normalizeBaseUrl(settings.baseUrl())
                    + "/drive/v3/files/" + encode(folderId)
                    + "?fields=id,name,mimeType&supportsAllDrives=true";
            HttpResponse<String> response = sendGet(url, token, timeout(settings));
            if (!isSuccessful(response.statusCode())) {
                return new DriveConnectionResult(false,
                        "Google Drive returned HTTP " + response.statusCode() + ": " + truncate(response.body()),
                        null, null);
            }
            JsonNode folder = objectMapper.readTree(response.body());
            return new DriveConnectionResult(true,
                    "Connected to Google Drive folder successfully.",
                    folder.path("name").asText("Configured folder"),
                    null);
        } catch (Exception ex) {
            log.warn("Google Drive connection test failed: {}", ex.getMessage());
            return new DriveConnectionResult(false, ex.getMessage(), null, null);
        }
    }

    public DriveUploadResult upload(GoogleDriveSettings settings, Path file, String fileName) {
        if (file == null || !Files.isRegularFile(file)) {
            throw new GoogleDriveException("Backup file was not found: " + file);
        }
        if (fileName == null || fileName.isBlank()) {
            throw new GoogleDriveException("A file name is required for the Google Drive upload.");
        }
        try {
            String token = accessToken(settings);
            String folderId = required(settings.folderId(), "Google Drive folder ID is required.");
            long size = Files.size(file);
            String metadata = objectMapper.writeValueAsString(Map.of(
                    "name", fileName,
                    "parents", List.of(folderId)));

            URI initUri = URI.create(normalizeBaseUrl(settings.baseUrl())
                    + "/upload/drive/v3/files?uploadType=resumable&supportsAllDrives=true"
                    + "&fields=id,name,webViewLink,size");
            HttpRequest initRequest = HttpRequest.newBuilder(initUri)
                    .timeout(Duration.ofMillis(timeout(settings)))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .header("X-Upload-Content-Type", "application/octet-stream")
                    .header("X-Upload-Content-Length", String.valueOf(size))
                    .POST(HttpRequest.BodyPublishers.ofString(metadata, StandardCharsets.UTF_8))
                    .build();

            HttpClient client = httpClient(settings);
            HttpResponse<String> initResponse = client.send(initRequest, HttpResponse.BodyHandlers.ofString());
            if (!isSuccessful(initResponse.statusCode())) {
                throw new GoogleDriveException("Google Drive rejected the upload start: HTTP "
                        + initResponse.statusCode() + " " + truncate(initResponse.body()));
            }
            String location = initResponse.headers().firstValue("Location")
                    .orElseThrow(() -> new GoogleDriveException(
                            "Google Drive did not return a resumable upload URL."));

            HttpRequest uploadRequest = HttpRequest.newBuilder(URI.create(location))
                    .timeout(Duration.ofMinutes(10))
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/octet-stream")
                    .PUT(HttpRequest.BodyPublishers.ofFile(file))
                    .build();
            HttpResponse<String> uploadResponse = client.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
            if (!isSuccessful(uploadResponse.statusCode())) {
                throw new GoogleDriveException("Google Drive upload failed: HTTP "
                        + uploadResponse.statusCode() + " " + truncate(uploadResponse.body()));
            }

            JsonNode result = objectMapper.readTree(uploadResponse.body());
            return new DriveUploadResult(
                    result.path("id").asText(),
                    result.path("name").asText(fileName),
                    result.path("webViewLink").asText(null),
                    result.path("size").asLong(size));
        } catch (GoogleDriveException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GoogleDriveException("Google Drive upload failed: " + ex.getMessage(), ex);
        }
    }

    private String accessToken(GoogleDriveSettings settings) {
        String mode = normalizeMode(settings.authMode());
        if ("MOCK".equals(mode)) {
            return MOCK_TOKEN;
        }

        // OAuth access tokens are cached by GoogleOAuthTokenService and shared
        // with Gmail, so one refresh token serves Drive backups and email.
        if ("OAUTH".equals(mode)) {
            return oauthTokenService.accessToken(
                    settings.credentialsPath(), tokenUri(settings), timeout(settings));
        }

        String cacheKey = mode + "|" + settings.credentialsPath() + "|" + tokenUri(settings);
        CachedToken cached = tokenCache.get(cacheKey);
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return cached.token();
        }

        TokenResponse token = serviceAccountToken(settings);
        long ttl = Math.max(token.expiresInSeconds() - 60, 60);
        tokenCache.put(cacheKey, new CachedToken(token.accessToken(), Instant.now().plusSeconds(ttl)));
        return token.accessToken();
    }

    private TokenResponse serviceAccountToken(GoogleDriveSettings settings) {
        JsonNode credentials = credentialsService.readJson(settings.credentialsPath());
        String email = required(credentials.path("client_email").asText(""),
                "The service-account file is missing client_email.");
        String privateKey = required(credentials.path("private_key").asText(""),
                "The service-account file is missing private_key.");
        String tokenUri = credentials.path("token_uri").asText(tokenUri(settings));
        String assertion = signServiceAccountJwt(email, privateKey, tokenUri);

        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer");
        form.put("assertion", assertion);
        return parseTokenResponse(postForm(tokenUri, form, timeout(settings)));
    }

    private String signServiceAccountJwt(String email, String privateKeyPem, String tokenUri) {
        try {
            String header = base64Url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}"
                    .getBytes(StandardCharsets.UTF_8));
            long now = Instant.now().getEpochSecond();
            String claims = objectMapper.writeValueAsString(Map.of(
                    "iss", email,
                    "scope", DRIVE_SCOPE,
                    "aud", tokenUri,
                    "iat", now,
                    "exp", now + 3600));
            String unsigned = header + "." + base64Url(claims.getBytes(StandardCharsets.UTF_8));

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(parsePrivateKey(privateKeyPem));
            signature.update(unsigned.getBytes(StandardCharsets.UTF_8));
            return unsigned + "." + base64Url(signature.sign());
        } catch (GoogleDriveException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GoogleDriveException("Could not sign the Google service-account request.", ex);
        }
    }

    private PrivateKey parsePrivateKey(String pem) {
        try {
            String normalized = pem
                    .replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "")
                    .replaceAll("\\s", "");
            byte[] der = Base64.getDecoder().decode(normalized);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception ex) {
            throw new GoogleDriveException("The service-account private key is invalid.", ex);
        }
    }

    private String postForm(String tokenUri, Map<String, String> form, int timeoutMs) {
        try {
            String body = form.entrySet().stream()
                    .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                    .collect(Collectors.joining("&"));
            HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUri))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient(timeoutMs).send(
                    request, HttpResponse.BodyHandlers.ofString());
            if (!isSuccessful(response.statusCode())) {
                throw new GoogleDriveException("Google token endpoint returned HTTP "
                        + response.statusCode() + ": " + truncate(response.body()));
            }
            return response.body();
        } catch (GoogleDriveException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GoogleDriveException("Could not obtain a Google Drive access token.", ex);
        }
    }

    private TokenResponse parseTokenResponse(String body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            String token = json.path("access_token").asText("");
            if (token.isBlank()) {
                String error = json.path("error_description").asText(
                        json.path("error").asText("Unknown token error"));
                throw new GoogleDriveException("Google token request failed: " + error);
            }
            return new TokenResponse(token, json.path("expires_in").asLong(3600));
        } catch (GoogleDriveException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new GoogleDriveException("Google returned an unreadable token response.", ex);
        }
    }

    private HttpResponse<String> sendGet(String url, String token, int timeoutMs) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        return httpClient(timeoutMs).send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpClient httpClient(GoogleDriveSettings settings) {
        return httpClient(timeout(settings));
    }

    private HttpClient httpClient(int timeoutMs) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.min(Math.max(timeoutMs, 1000), 10000)))
                .build();
    }

    private int timeout(GoogleDriveSettings settings) {
        return settings.timeoutMs() <= 0 ? 30000 : settings.timeoutMs();
    }

    private String normalizeBaseUrl(String value) {
        String base = value == null || value.isBlank() ? DEFAULT_BASE_URL : value.trim();
        return base.replaceAll("/+$", "");
    }

    private String tokenUri(GoogleDriveSettings settings) {
        String fromSettings = settings.tokenUri();
        if (fromSettings != null && !fromSettings.isBlank()) {
            return fromSettings.trim();
        }
        return DEFAULT_TOKEN_URI;
    }

    private String normalizeMode(String value) {
        return value == null || value.isBlank()
                ? "SERVICE_ACCOUNT"
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new GoogleDriveException(message);
        }
        return value.trim();
    }

    private boolean isSuccessful(int status) {
        return status >= 200 && status < 300;
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private String truncate(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "…";
    }

    private record TokenResponse(String accessToken, long expiresInSeconds) {
    }

    private record CachedToken(String token, Instant expiresAt) {
    }
}
