package com.sacco.collateral.dto;

import com.sacco.collateral.entity.CollateralAsset;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CollateralRequest {

    @NotNull(message = "Member is required")
    private Long memberId;

    private Long loanAccountId;

    @NotNull(message = "Asset type is required")
    private CollateralAsset.AssetType assetType;

    @NotBlank(message = "Title / registration number is required")
    private String titleNumber;

    private String description;

    @NotNull(message = "Estimated / market value is required")
    @DecimalMin(value = "0.0", message = "Estimated value cannot be negative")
    private BigDecimal estimatedValue;

    @DecimalMin(value = "0.0", message = "Forced sale value cannot be negative")
    private BigDecimal forcedSaleValue;

    private LocalDate valuationDate;
    private String valuerName;
    private String registeredOwnerName;
    private String custodianLocation;
}
