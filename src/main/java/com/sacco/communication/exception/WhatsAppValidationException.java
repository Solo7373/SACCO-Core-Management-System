package com.sacco.communication.exception;

/** A caller mistake: bad number, missing template parameter, invalid media, etc. */
public class WhatsAppValidationException extends RuntimeException {
    public WhatsAppValidationException(String message) {
        super(message);
    }
}
