package com.sacco.communication.dto;

/**
 * One recipient entry from the Africa's Talking messaging response.
 * statusCode 100/101/102 means the provider accepted the message.
 */
public record SmsRecipientResult(
        String number,
        int statusCode,
        String status,
        String messageId,
        String cost,
        boolean success,
        boolean retryable,
        String failureReason
) {
}
