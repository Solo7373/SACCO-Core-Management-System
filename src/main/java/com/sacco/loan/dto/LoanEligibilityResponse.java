package com.sacco.loan.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class LoanEligibilityResponse {
    private Long memberId;
    private String memberName;
    private String membershipNo;
    private BigDecimal savingsBalance;
    private BigDecimal shareValue;
    private BigDecimal totalCapacity;
    private BigDecimal maxMultiplier;
    private BigDecimal maximumEligible;
    private BigDecimal requestedAmount;
    private boolean eligible;
    private List<String> reasons;
}
