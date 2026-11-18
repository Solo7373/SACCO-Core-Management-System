package com.sacco.communication.email;

/**
 * The Gmail API rejected the request or could not be reached.
 */
public class EmailDeliveryException extends EmailException {

    public EmailDeliveryException(String message) {
        super(message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
