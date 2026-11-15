package com.sacco.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class TaxRateRequest {
    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Code is required")
    private String code;

    @NotBlank(message = "Category is required")
    private String category;

    @NotNull(message = "Rate percentage is required")
    @DecimalMin(value = "0.0", message = "Rate cannot be negative")
    private BigDecimal ratePercent;

    private Boolean appliesToFees = true;
    private Boolean isActive = true;
    private String description;
    private LocalDate effectiveFrom;
}
