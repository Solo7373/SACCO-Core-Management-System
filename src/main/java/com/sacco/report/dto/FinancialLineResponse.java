package com.sacco.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class FinancialLineResponse {
    private String accountCode;
    private String accountName;
    private BigDecimal amount;
}
