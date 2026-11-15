package com.sacco.communication.dto;

public record WhatsAppSummaryResponse(
        long total,
        long queued,
        long sending,
        long sent,
        long delivered,
        long read,
        long failed,
        long optedIn,
        long optedOut,
        long inbound) {
}
