package com.sacco.storage.google;

/**
 * Raised when a Google OAuth token cannot be obtained or refreshed.
 */
public class GoogleTokenException extends RuntimeException {

    public GoogleTokenException(String message) {
        super(message);
    }

    public GoogleTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
