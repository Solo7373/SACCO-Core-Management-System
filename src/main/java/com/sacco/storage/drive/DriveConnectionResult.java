package com.sacco.storage.drive;

public record DriveConnectionResult(
        boolean connected,
        String message,
        String folderName,
        String account) {
}
