package com.sacco.communication.exception;

/**
 * Free-form text was requested outside the 24-hour customer service window.
 * Meta requires an approved template in that case.
 */
public class WhatsAppWindowClosedException extends RuntimeException {
    public WhatsAppWindowClosedException(String message) {
        super(message);
    }
}
