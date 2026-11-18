package com.sacco.communication.email;

/**
 * Email sending has not been connected yet, or the selected Google
 * authentication mode cannot access a Gmail mailbox.
 */
public class EmailNotConfiguredException extends EmailException {

    public EmailNotConfiguredException(String message) {
        super(message);
    }
}
