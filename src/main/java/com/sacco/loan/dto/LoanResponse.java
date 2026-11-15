package com.sacco.loan.dto;

import com.sacco.loan.entity.Loan;
import com.sacco.loan.entity.LoanProduct;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class LoanResponse {
    private Long loanId;
    private String loanNumber;
    private Long memberId;
    private String memberName;
    private String membershipNo;
    private Long productId;
    private String productName;
    private BigDecimal amount;
    private BigDecimal interestRate;
    private LoanProduct.InterestMethod interestMethod;
    private Integer termMonths;
    private String purpose;
    private Loan.LoanStatus status;
    private LocalDate applicationDate;
    private Long reviewedBy;
    private LocalDateTime reviewedAt;
    private String eligibilityNotes;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private Long rejectedBy;
    private LocalDateTime rejectedAt;
    private String rejectionReason;
    private Long disbursedBy;
    private LocalDateTime disbursedAt;
    private Long disbursementAccountId;
    private BigDecimal outstandingPrincipal;
    private BigDecimal outstandingInterest;
    private BigDecimal totalPaid;
    private BigDecimal totalInterest;
    private BigDecimal totalRepayable;
    private BigDecimal amountInArrears;
    private BigDecimal penaltyDue;
    private LocalDate nextDueDate;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private List<LoanGuarantorResponse> guarantors;
}
