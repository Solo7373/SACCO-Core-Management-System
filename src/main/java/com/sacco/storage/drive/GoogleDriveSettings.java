package com.sacco.storage.drive;

/**
 * Effective Google Drive settings for one operation. Built from the
 * first-run setup row, with environment properties as a fallback.
 */
public record GoogleDriveSettings(
        String authMode,
        String baseUrl,
        String tokenUri,
        String credentialsPath,
        String folderId,
        String applicationName,
        int timeoutMs) {
}
