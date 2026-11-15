package com.sacco.communication.dto;

import com.sacco.communication.entity.SmsMessage;

import java.time.LocalDateTime;

public record SmsMessageResponse(
        Long messageId,
        String reference,
        String batchId,
        String recipient,
        String senderId,
        String messageType,
        String status,
        String providerMessageId,
        String providerStatus,
        Integer providerStatusCode,
        String providerCost,
        String failureReason,
        int attempts,
        LocalDateTime nextAttemptAt,
        LocalDateTime sentAt,
        LocalDateTime deliveredAt,
        LocalDateTime createdAt
) {
    public static SmsMessageResponse from(SmsMessage message) {
        return new SmsMessageResponse(
                message.getMessageId(),
                message.getReference(),
                message.getBatchId(),
                message.getRecipient(),
                message.getSenderId(),
                message.getMessageType() == null ? null : message.getMessageType().name(),
                message.getStatus() == null ? null : message.getStatus().name(),
                message.getProviderMessageId(),
                message.getProviderStatus(),
                message.getProviderStatusCode(),
                message.getProviderCost(),
                message.getFailureReason(),
                message.getAttempts(),
                message.getNextAttemptAt(),
                message.getSentAt(),
                message.getDeliveredAt(),
                message.getCreatedAt());
    }
}
