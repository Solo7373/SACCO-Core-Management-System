package com.sacco.loan.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class CollectionLoanRow {
    private Long loanId;
    private String loanNumber;
    private Long memberId;
    private String memberName;
    private String membershipNo;
    private String phone;
    private String email;
    private String productName;
    private String loanStatus;
    private BigDecimal outstandingPrincipal;
    private BigDecimal outstandingInterest;
    private BigDecimal totalOutstanding;
    private BigDecimal principalArrears;
    private BigDecimal interestArrears;
    private BigDecimal amountInArrears;
    private BigDecimal penaltyOutstanding;
    private BigDecimal amountDemanded;
    private int daysOverdue;
    private String agingBucket;
    private String suggestedStage;
    private String lastActionType;
    private LocalDateTime lastActionAt;
    private String lastDemandStage;
    private LocalDateTime lastDemandAt;
    private long demandsIssued;
    private BigDecimal promisedAmount;
    private LocalDate promisedDate;
    private String promiseStatus;
    private LocalDate nextDueDate;
    private LocalDateTime disbursedAt;
}
