package com.sacco.communication.exception;

/** A webhook token or source IP failed validation. */
public class WebhookAuthenticationException extends RuntimeException {
    public WebhookAuthenticationException(String message) {
        super(message);
    }
}
