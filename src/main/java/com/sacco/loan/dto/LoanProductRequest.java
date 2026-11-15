package com.sacco.loan.dto;

import com.sacco.loan.entity.LoanProduct;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LoanProductRequest {
    @NotBlank(message = "Product name is required")
    private String name;

    private String description;

    @NotNull(message = "Interest rate is required")
    @DecimalMin(value = "0.0", message = "Interest rate cannot be negative")
    private BigDecimal interestRate;

    private LoanProduct.InterestMethod interestMethod = LoanProduct.InterestMethod.REDUCING_BALANCE;

    @NotNull(message = "Maximum term is required")
    @Min(value = 1, message = "Maximum term must be at least one month")
    private Integer maxTermMonths;

    private BigDecimal minAmount = BigDecimal.ZERO;
    private BigDecimal maxAmount;
    private BigDecimal maxMultiplier = new BigDecimal("3.00");
    private BigDecimal penaltyRate = BigDecimal.ZERO;
    private Boolean requiresGuarantor = false;
}
