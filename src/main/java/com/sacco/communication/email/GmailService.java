package com.sacco.communication.email;

import com.sacco.setup.SystemSettingsService;
import com.sacco.storage.google.GmailProperties;
import com.sacco.storage.google.GmailSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Facade over the Gmail client. It resolves the effective sender and OAuth
 * credentials from the single setup row so callers never handle secrets.
 */
@Service
@RequiredArgsConstructor
public class GmailService {

    private final GmailClient client;
    private final SystemSettingsService settingsService;
    private final GmailProperties properties;

    public GmailConnectionResult testConnection() {
        requireEnabled();
        return client.testConnection(settingsService.gmailSettings());
    }

    public GmailSendResult send(String to, String subject, String body) {
        requireEnabled();
        if (!settingsService.isEmailConfigured()) {
            throw new EmailNotConfiguredException(
                    "Gmail has not been connected yet. Complete the Gmail connection test in "
                            + "system setup before sending email.");
        }
        return client.send(settingsService.gmailSettings(), to, subject, body);
    }

    /**
     * Used by the setup wizard's test-send button. It allows the first
     * successful send to establish the connection, so it deliberately skips
     * the {@code emailConnected} flag check while still requiring credentials
     * and a sender address.
     */
    public GmailSendResult sendFromSetup(String to, String subject, String body) {
        requireEnabled();
        return client.send(settingsService.gmailSettings(), to, subject, body);
    }

    public boolean isConfigured() {
        return properties.isEnabled() && settingsService.isEmailConfigured();
    }

    public GmailSettings settings() {
        return settingsService.gmailSettings();
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new EmailNotConfiguredException(
                    "Gmail sending is disabled. Set GMAIL_ENABLED=true to use outbound email.");
        }
    }
}
