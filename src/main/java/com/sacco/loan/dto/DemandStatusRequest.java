package com.sacco.loan.dto;

import com.sacco.loan.entity.LoanDemand;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DemandStatusRequest {
    @NotNull(message = "Demand status is required")
    private LoanDemand.DemandStatus status;

    private String notes;
}
