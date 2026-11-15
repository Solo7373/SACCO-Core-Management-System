package com.sacco.loan.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class LoanDemandResponse {
    private Long demandId;
    private String demandNumber;
    private Long loanId;
    private String loanNumber;
    private Long memberId;
    private String memberName;
    private String stage;
    private BigDecimal amountDemanded;
    private BigDecimal principalArrears;
    private BigDecimal interestArrears;
    private BigDecimal penaltyArrears;
    private int daysOverdue;
    private Long issuedBy;
    private String issuedByName;
    private LocalDateTime issuedAt;
    private LocalDate dueDate;
    private String deliveryMethod;
    private String status;
    private String notes;
    private String documentSeal;
}
