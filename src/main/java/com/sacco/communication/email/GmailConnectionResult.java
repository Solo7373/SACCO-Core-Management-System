package com.sacco.communication.email;

/**
 * Result of a Gmail profile connection test.
 */
public record GmailConnectionResult(
        boolean connected,
        String message,
        String emailAddress,
        long messagesTotal) {
}
