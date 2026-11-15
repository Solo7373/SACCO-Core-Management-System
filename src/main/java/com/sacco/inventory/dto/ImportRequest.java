package com.sacco.inventory.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ImportRequest {
    @NotNull(message = "Product is required")
    private Long productId;

    @NotBlank(message = "Supplier name is required")
    private String supplierName;

    private String supplierCountry;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "Unit cost is required")
    @DecimalMin(value = "0.0", message = "Unit cost cannot be negative")
    private BigDecimal unitCost;

    @NotBlank(message = "Shipping method is required")
    private String shippingMethod;

    private BigDecimal shippingFee;

    @DecimalMin(value = "0.0", message = "Customs duty rate cannot be negative")
    private BigDecimal customsDutyRate = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", message = "VAT rate cannot be negative")
    private BigDecimal vatRate = BigDecimal.ZERO;

    @DecimalMin(value = "0.0", message = "Other taxes cannot be negative")
    private BigDecimal otherTaxes = BigDecimal.ZERO;

    private LocalDate orderDate;
    private LocalDate expectedDate;
    private String notes;
}
