package com.sacco.communication.dto;

public record SmsSummaryResponse(
        long total,
        long queued,
        long sending,
        long sent,
        long delivered,
        long failed,
        long rejected,
        long expired
) {
}
