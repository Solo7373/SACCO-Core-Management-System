package com.sacco.loan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class LoanApplicationRequest {
    @NotNull(message = "Member ID is required")
    private Long memberId;

    @NotNull(message = "Loan product is required")
    private Long productId;

    @NotNull(message = "Loan amount is required")
    @Positive(message = "Loan amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Repayment term is required")
    @Min(value = 1, message = "Repayment term must be at least one month")
    private Integer termMonths;

    private String purpose;

    @Valid
    private List<GuarantorRequest> guarantors = new ArrayList<>();
}
