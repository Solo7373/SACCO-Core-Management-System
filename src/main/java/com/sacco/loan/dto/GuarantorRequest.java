package com.sacco.loan.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class GuarantorRequest {
    @NotNull(message = "Guarantor member is required")
    private Long memberId;

    @NotNull(message = "Guaranteed amount is required")
    @Positive(message = "Guaranteed amount must be positive")
    private BigDecimal guaranteedAmount;
}
