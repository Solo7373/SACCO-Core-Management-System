package com.sacco.storage.drive;

public class GoogleDriveException extends RuntimeException {
    public GoogleDriveException(String message) {
        super(message);
    }

    public GoogleDriveException(String message, Throwable cause) {
        super(message, cause);
    }
}
