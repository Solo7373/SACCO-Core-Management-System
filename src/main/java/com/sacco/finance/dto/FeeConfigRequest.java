package com.sacco.finance.dto;

import com.sacco.finance.entity.FeeConfig;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class FeeConfigRequest {
    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Transaction type is required")
    private String transactionType;

    @NotBlank(message = "Channel is required")
    private String channel;

    @NotNull(message = "Fee type is required")
    private FeeConfig.FeeType feeType;

    @NotNull(message = "Fee value is required")
    @DecimalMin(value = "0.0", message = "Fee value cannot be negative")
    private BigDecimal feeValue;

    private BigDecimal minAmount;
    private BigDecimal maxFee;
    private Long taxRateId;
    private Boolean isActive = true;
    private String description;
}
