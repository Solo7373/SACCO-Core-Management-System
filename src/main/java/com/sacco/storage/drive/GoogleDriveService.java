package com.sacco.storage.drive;

import com.sacco.setup.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

/**
 * Facade used by the setup wizard and the backup engine. It resolves the
 * effective Drive settings from the single system-settings row and delegates
 * to the stateless HTTP client.
 */
@Service
@RequiredArgsConstructor
public class GoogleDriveService {

    private final GoogleDriveClient client;
    private final SystemSettingsService settingsService;

    public DriveConnectionResult testConnection() {
        return client.testConnection(settingsService.driveSettings());
    }

    public DriveUploadResult uploadBackup(Path source, String fileName) {
        return client.upload(settingsService.driveSettings(), source, fileName);
    }

    public boolean isConfigured() {
        return settingsService.isDriveConfigured();
    }
}
