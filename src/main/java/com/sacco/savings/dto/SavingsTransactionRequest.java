package com.sacco.savings.dto;

import com.sacco.savings.entity.SavingsTransaction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class SavingsTransactionRequest {
    @NotNull(message = "Account ID is required")
    private Long accountId;

    @NotNull(message = "Transaction type is required")
    private SavingsTransaction.SavingsTransactionType transactionType;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private String currency = "UGX";
    private String externalReference;
    private String description;
    private LocalDate valueDate;

    /** Channel used by the fee/tax engine: CASH, BANK, MOBILE_MONEY, CROSS_BANK. */
    private String channel = "CASH";

    /** Required when a withdrawal/transfer-out is at or above the approval threshold. */
    private String authorizedBy;

    private String approvalReference;
}
