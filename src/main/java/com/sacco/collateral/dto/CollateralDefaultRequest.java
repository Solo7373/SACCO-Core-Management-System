package com.sacco.collateral.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CollateralDefaultRequest {

    @NotBlank(message = "Reason for default / auction is required")
    private String reason;
}
