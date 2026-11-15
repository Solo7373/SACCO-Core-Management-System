package com.sacco.communication.exception;

/** Meta credentials are missing or the integration is disabled. */
public class WhatsAppProviderUnavailableException extends RuntimeException {
    public WhatsAppProviderUnavailableException(String message) {
        super(message);
    }
}
