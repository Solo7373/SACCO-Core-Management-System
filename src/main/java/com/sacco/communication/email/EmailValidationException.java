package com.sacco.communication.email;

/**
 * Invalid recipient, subject or message body supplied by a caller.
 */
public class EmailValidationException extends EmailException {

    public EmailValidationException(String message) {
        super(message);
    }
}
