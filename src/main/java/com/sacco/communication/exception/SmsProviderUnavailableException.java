package com.sacco.communication.exception;

/** The provider is disabled or credentials are missing; the operation can be retried later. */
public class SmsProviderUnavailableException extends RuntimeException {
    public SmsProviderUnavailableException(String message) {
        super(message);
    }
}
