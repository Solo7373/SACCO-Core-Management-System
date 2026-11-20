package com.sacco.communication.voice.exception;

/**
 * Base voice integration failure. Transient provider faults may expose a
 * retryable flag through subclasses that support it.
 */
public class VoiceException extends RuntimeException {
    public VoiceException(String message) {
        super(message);
    }
}
