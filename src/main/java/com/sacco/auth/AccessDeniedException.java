package com.sacco.auth;

/**
 * Raised when an authenticated actor lacks the permission required for an
 * action. Mapped to HTTP 403 by the global exception handler.
 */
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
