package com.sacco.account.dto;

import com.sacco.account.entity.Account;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AccountRequest {
    @NotNull(message = "Account type is required")
    private Account.AccountType accountType;
    private String currency = "UGX";
    private Long branchId;

    @Min(value = 1, message = "Fixed deposit term must be at least one month")
    private Integer fixedDepositTermMonths;

    @DecimalMin(value = "0.0", message = "Fixed deposit interest rate cannot be negative")
    private BigDecimal fixedDepositInterestRate;
}
