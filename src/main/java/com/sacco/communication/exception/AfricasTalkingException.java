package com.sacco.communication.exception;

/**
 * A provider call failed. {@code retryable} distinguishes transient faults
 * (timeouts, 429, 5xx, routing failures) from permanent ones (bad credentials,
 * invalid number, insufficient balance).
 */
public class AfricasTalkingException extends RuntimeException {

    private final boolean retryable;

    public AfricasTalkingException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public AfricasTalkingException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
