package com.sacco.communication.dto;

import com.sacco.communication.entity.WhatsAppInboundMessage;

import java.time.LocalDateTime;

public record WhatsAppInboundResponse(
        Long inboundId,
        String providerMessageId,
        String fromNumber,
        String contactName,
        String messageType,
        String body,
        String mediaId,
        String mediaMimeType,
        String contextMessageId,
        LocalDateTime receivedAt,
        boolean processed) {

    public static WhatsAppInboundResponse from(WhatsAppInboundMessage message) {
        return new WhatsAppInboundResponse(
                message.getInboundId(),
                message.getProviderMessageId(),
                message.getFromNumber(),
                message.getContactName(),
                message.getMessageType(),
                message.getBody(),
                message.getMediaId(),
                message.getMediaMimeType(),
                message.getContextMessageId(),
                message.getReceivedAt(),
                message.isProcessed());
    }
}
