package com.sacco.communication.dto;

import java.time.LocalDateTime;

public record OtpVerifyResponse(
        boolean verified,
        String maskedPhone,
        LocalDateTime verifiedAt
) {
}
