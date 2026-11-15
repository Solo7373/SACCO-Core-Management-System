package com.sacco.loan.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class LoanScheduleResponse {
    private Long scheduleId;
    private Integer installmentNo;
    private LocalDate dueDate;
    private BigDecimal principalDue;
    private BigDecimal interestDue;
    private BigDecimal totalDue;
    private BigDecimal principalPaid;
    private BigDecimal interestPaid;
    private BigDecimal penaltyDue;
    private BigDecimal penaltyPaid;
    private BigDecimal totalPaid;
    private BigDecimal balance;
    private long daysOverdue;
    private BigDecimal accruedPenalty;
    private String status;
}
