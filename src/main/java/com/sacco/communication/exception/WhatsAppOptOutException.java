package com.sacco.communication.exception;

/** The member has opted out of WhatsApp messages. */
public class WhatsAppOptOutException extends RuntimeException {
    public WhatsAppOptOutException(String message) {
        super(message);
    }
}
