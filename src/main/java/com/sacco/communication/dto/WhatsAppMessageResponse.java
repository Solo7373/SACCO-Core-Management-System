package com.sacco.communication.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sacco.communication.entity.WhatsAppMessage;

import java.time.LocalDateTime;
import java.util.List;

public record WhatsAppMessageResponse(
        Long messageId,
        String reference,
        String batchId,
        String recipient,
        String recipientWaId,
        WhatsAppMessage.WhatsAppMessageType type,
        WhatsAppMessage.WhatsAppStatus status,
        String templateName,
        String templateLanguage,
        List<String> templateParameters,
        String body,
        String providerMessageId,
        String providerStatus,
        Integer errorCode,
        Integer errorSubcode,
        String errorTitle,
        String errorMessage,
        String fbtraceId,
        int attempts,
        LocalDateTime nextAttemptAt,
        LocalDateTime sentAt,
        LocalDateTime deliveredAt,
        LocalDateTime readAt,
        LocalDateTime failedAt,
        LocalDateTime createdAt) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static WhatsAppMessageResponse from(WhatsAppMessage message) {
        return new WhatsAppMessageResponse(
                message.getMessageId(),
                message.getReference(),
                message.getBatchId(),
                message.getRecipient(),
                message.getRecipientWaId(),
                message.getMessageType(),
                message.getStatus(),
                message.getTemplateName(),
                message.getTemplateLanguage(),
                parseParameters(message.getTemplateParameters()),
                message.getBody(),
                message.getProviderMessageId(),
                message.getProviderStatus(),
                message.getErrorCode(),
                message.getErrorSubcode(),
                message.getErrorTitle(),
                message.getErrorMessage(),
                message.getFbtraceId(),
                message.getAttempts(),
                message.getNextAttemptAt(),
                message.getSentAt(),
                message.getDeliveredAt(),
                message.getReadAt(),
                message.getFailedAt(),
                message.getCreatedAt());
    }

    private static List<String> parseParameters(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception exception) {
            return List.of();
        }
    }
}
