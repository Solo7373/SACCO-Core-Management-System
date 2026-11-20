package com.sacco.communication.voice.exception;

/**
 * Africa's Talking voice credentials are missing or the voice product is not
 * enabled on the account. The call can be retried once the operator completes
 * the Africa Talking voice setup.
 */
public class VoiceProviderUnavailableException extends VoiceException {
    public VoiceProviderUnavailableException(String message) {
        super(message);
    }
}
