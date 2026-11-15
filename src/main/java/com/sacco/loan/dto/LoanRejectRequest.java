package com.sacco.loan.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoanRejectRequest {
    @NotBlank(message = "Rejection reason is required")
    private String reason;
}
