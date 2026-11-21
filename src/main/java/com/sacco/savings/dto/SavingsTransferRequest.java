package com.sacco.savings.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SavingsTransferRequest {
    @NotNull(message = "Source account is required")
    private Long fromAccountId;

    @NotNull(message = "Destination account is required")
    private Long toAccountId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private String description;
    private String authorizedBy;
    private String approvalReference;

    /** Transfer channel: INTERNAL, BANK, CROSS_BANK, MOBILE_MONEY. */
    private String channel = "INTERNAL";
}
