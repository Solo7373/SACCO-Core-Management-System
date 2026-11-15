package com.sacco.ledger.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class TrialBalanceResponse {
    private String accountCode;
    private String accountName;
    private String category;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private BigDecimal netBalance;
    private LocalDate generatedAt;
}
