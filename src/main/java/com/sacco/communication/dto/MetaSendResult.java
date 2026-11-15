package com.sacco.communication.dto;

/** Minimal successful response from Meta's /messages endpoint. */
public record MetaSendResult(
        String providerMessageId,
        String recipientWaId,
        String providerStatus) {
}
