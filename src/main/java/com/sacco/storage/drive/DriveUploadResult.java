package com.sacco.storage.drive;

public record DriveUploadResult(
        String fileId,
        String name,
        String webViewLink,
        long size) {
}
