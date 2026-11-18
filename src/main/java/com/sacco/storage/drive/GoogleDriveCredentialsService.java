package com.sacco.storage.drive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stores Google credentials outside the database and outside the repository.
 * The setup row only records the absolute path. On POSIX systems the files are
 * written with owner-read/write permissions; on Windows the per-user profile
 * directory provides the first layer of protection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleDriveCredentialsService {

    private static final String SERVICE_ACCOUNT_FILE = "google-service-account.json";
    private static final String OAUTH_FILE = "google-oauth.json";

    private final ObjectMapper objectMapper;

    @Value("${sacco.google-drive.secrets-dir:}")
    private String configuredSecretsDir;

    public Path secretsDirectory() {
        if (configuredSecretsDir != null && !configuredSecretsDir.isBlank()) {
            return Path.of(configuredSecretsDir.trim()).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), ".sacco", "secrets")
                .toAbsolutePath()
                .normalize();
    }

    public StoredCredential storeServiceAccount(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new GoogleDriveException("Choose a service-account JSON file first.");
        }
        try {
            JsonNode json = objectMapper.readTree(file.getInputStream());
            String email = json.path("client_email").asText("").trim();
            String privateKey = json.path("private_key").asText("").trim();
            if (email.isBlank() || privateKey.isBlank()) {
                throw new GoogleDriveException(
                        "The file is not a valid Google service-account key. It must contain client_email and private_key.");
            }
            Path directory = secretsDirectory();
            Files.createDirectories(directory);
            Path target = directory.resolve(SERVICE_ACCOUNT_FILE);
            Files.writeString(target, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json),
                    StandardCharsets.UTF_8);
            restrictPermissions(target);
            log.info("Stored Google service-account credentials for {}", email);
            return new StoredCredential(target.toString(), target.getFileName().toString(), email);
        } catch (GoogleDriveException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new GoogleDriveException("Could not read or store the service-account file.", ex);
        }
    }

    public StoredCredential storeOAuthCredentials(String clientId, String clientSecret, String refreshToken) {
        if (isBlank(clientId) || isBlank(clientSecret) || isBlank(refreshToken)) {
            throw new GoogleDriveException(
                    "OAuth setup requires the client ID, client secret and refresh token.");
        }
        try {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("client_id", clientId.trim());
            values.put("client_secret", clientSecret.trim());
            values.put("refresh_token", refreshToken.trim());
            Path directory = secretsDirectory();
            Files.createDirectories(directory);
            Path target = directory.resolve(OAUTH_FILE);
            Files.writeString(target, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(values),
                    StandardCharsets.UTF_8);
            restrictPermissions(target);
            log.info("Stored Google OAuth refresh credentials");
            return new StoredCredential(target.toString(), target.getFileName().toString(), null);
        } catch (IOException ex) {
            throw new GoogleDriveException("Could not store the OAuth credentials.", ex);
        }
    }

    public JsonNode readJson(String path) {
        if (isBlank(path)) {
            throw new GoogleDriveException("Google Drive credentials have not been configured.");
        }
        Path file = Path.of(path).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            throw new GoogleDriveException("Google Drive credentials file was not found: " + file.getFileName());
        }
        try {
            return objectMapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new GoogleDriveException("Could not read the Google Drive credentials file.", ex);
        }
    }

    public boolean exists(String path) {
        return !isBlank(path) && Files.isRegularFile(Path.of(path).toAbsolutePath().normalize());
    }

    /**
     * True when the stored OAuth file already contains exactly these values.
     * The setup wizard may re-submit the same credentials on later saves; in
     * that case the existing email test must remain valid and the cached
     * access token does not need to be cleared.
     */
    public boolean matchesOAuthCredentials(String path, String clientId,
                                           String clientSecret, String refreshToken) {
        if (!exists(path)) {
            return false;
        }
        try {
            JsonNode json = readJson(path);
            return sameValue(json.path("client_id").asText(""), clientId)
                    && sameValue(json.path("client_secret").asText(""), clientSecret)
                    && sameValue(json.path("refresh_token").asText(""), refreshToken);
        } catch (RuntimeException ex) {
            log.debug("Could not compare stored OAuth credentials: {}", ex.getMessage());
            return false;
        }
    }

    public String fileName(String path) {
        if (isBlank(path)) {
            return null;
        }
        Path file = Path.of(path).getFileName();
        return file == null ? null : file.toString();
    }

    private void restrictPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ex) {
            // Windows and some mounted filesystems do not support POSIX modes.
            log.debug("Could not set POSIX permissions on {}: {}", file, ex.getMessage());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean sameValue(String left, String right) {
        return left != null && right != null && left.trim().equals(right.trim());
    }

    public record StoredCredential(String path, String fileName, String accountEmail) {
    }
}
