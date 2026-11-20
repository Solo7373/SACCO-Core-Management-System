package com.sacco.communication.voice.exception;

/**
 * A voice call could not be initiated because of an invalid request, for
 * example a blank destination number or an unsupported script.
 */
public class VoiceValidationException extends VoiceException {
    public VoiceValidationException(String message) {
        super(message);
    }
}
