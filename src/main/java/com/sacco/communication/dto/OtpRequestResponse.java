package com.sacco.communication.dto;

import java.time.LocalDateTime;

public record OtpRequestResponse(
        String reference,
        String maskedPhone,
        LocalDateTime expiresAt,
        long ttlSeconds,
        String message
) {
}
