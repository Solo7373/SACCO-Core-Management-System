package com.sacco.share.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ShareProductRequest {
    @NotBlank(message = "Product name is required")
    private String name;

    private String description;

    @Min(value = 1, message = "Minimum shares must be at least 1")
    private Integer minShares = 1;

    @NotNull(message = "Price per share is required")
    @Min(value = 0, message = "Price per share cannot be negative")
    private BigDecimal pricePerShare;
}
