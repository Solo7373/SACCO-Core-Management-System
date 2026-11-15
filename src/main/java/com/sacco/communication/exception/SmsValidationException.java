package com.sacco.communication.exception;

/** The caller supplied a phone number, message or payload that cannot be accepted. */
public class SmsValidationException extends RuntimeException {
    public SmsValidationException(String message) {
        super(message);
    }
}
