package com.sacco.communication.exception;

/** The recipient previously replied STOP and has not opted back in. */
public class SmsOptOutException extends RuntimeException {
    public SmsOptOutException(String message) {
        super(message);
    }
}
