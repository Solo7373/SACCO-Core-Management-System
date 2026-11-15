package com.sacco.collateral.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class CollateralReleaseRequest {

    private String releasedTo;

    private LocalDate releaseDate;

    @NotBlank(message = "Authorizing manager is required")
    private String authorizedBy;

    @NotBlank(message = "Signature is required")
    private String signature;

    private String notes;
}
