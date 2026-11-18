package com.sacco.communication.email;

/**
 * Raised when an email cannot be validated, sent or configured.
 */
public class EmailException extends RuntimeException {

    public EmailException(String message) {
        super(message);
    }

    public EmailException(String message, Throwable cause) {
        super(message, cause);
    }
}
