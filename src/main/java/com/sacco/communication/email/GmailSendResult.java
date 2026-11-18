package com.sacco.communication.email;

/**
 * Result of one Gmail API send.
 */
public record GmailSendResult(
        String messageId,
        String threadId,
        String to,
        String from,
        String subject) {
}
