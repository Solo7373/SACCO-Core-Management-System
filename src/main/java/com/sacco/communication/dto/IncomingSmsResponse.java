package com.sacco.communication.dto;

import com.sacco.communication.entity.IncomingSms;

import java.time.LocalDateTime;

public record IncomingSmsResponse(
        Long incomingId,
        String providerMessageId,
        String sender,
        String recipient,
        String text,
        String linkId,
        LocalDateTime receivedAt,
        boolean processed
) {
    public static IncomingSmsResponse from(IncomingSms message) {
        return new IncomingSmsResponse(
                message.getIncomingId(),
                message.getProviderMessageId(),
                message.getSender(),
                message.getRecipient(),
                message.getText(),
                message.getLinkId(),
                message.getReceivedAt(),
                message.isProcessed());
    }
}
