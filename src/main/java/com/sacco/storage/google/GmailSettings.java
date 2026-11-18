package com.sacco.storage.google;

/**
 * Effective Gmail settings for one send operation. The Google account in the
 * OAuth refresh token is both the Drive owner and the email sender, so the
 * token URI and credentials path are shared with the Drive configuration.
 */
public record GmailSettings(
        String authMode,
        String baseUrl,
        String tokenUri,
        String credentialsPath,
        String fromAddress,
        String fromName,
        String replyTo,
        int timeoutMs) {
}
