package com.sacco.loan.dto;

import com.sacco.loan.entity.LoanRepayment;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class LoanRepaymentResponse {
    private Long repaymentId;
    private Long loanId;
    private String reference;
    private BigDecimal amount;
    private BigDecimal principalPortion;
    private BigDecimal interestPortion;
    private BigDecimal penaltyPortion;
    private LocalDate paymentDate;
    private String notes;
    private Long postedBy;
    private String status;
    private LocalDateTime createdAt;

    public static LoanRepaymentResponse fromEntity(LoanRepayment repayment) {
        return LoanRepaymentResponse.builder()
                .repaymentId(repayment.getRepaymentId())
                .loanId(repayment.getLoanId())
                .reference(repayment.getReference())
                .amount(repayment.getAmount())
                .principalPortion(repayment.getPrincipalPortion())
                .interestPortion(repayment.getInterestPortion())
                .penaltyPortion(repayment.getPenaltyPortion())
                .paymentDate(repayment.getPaymentDate())
                .notes(repayment.getNotes())
                .postedBy(repayment.getPostedBy())
                .status(repayment.getStatus())
                .createdAt(repayment.getCreatedAt())
                .build();
    }
}
