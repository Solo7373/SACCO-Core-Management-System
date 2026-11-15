package com.sacco.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class JournalLineRequest {
    @NotBlank(message = "Account code is required")
    private String accountCode;

    @NotNull(message = "Debit amount is required")
    @PositiveOrZero(message = "Debit cannot be negative")
    private BigDecimal debit = BigDecimal.ZERO;

    @NotNull(message = "Credit amount is required")
    @PositiveOrZero(message = "Credit cannot be negative")
    private BigDecimal credit = BigDecimal.ZERO;

    private String description;
}
