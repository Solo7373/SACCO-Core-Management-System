package com.sacco.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class BalanceSheetResponse {
    private LocalDate asOfDate;
    private List<FinancialLineResponse> assets;
    private BigDecimal totalAssets;
    private List<FinancialLineResponse> liabilities;
    private BigDecimal totalLiabilities;
    private List<FinancialLineResponse> equity;
    private BigDecimal totalEquity;
    private BigDecimal netSurplus;
    private BigDecimal totalLiabilitiesAndEquity;
    private boolean balanced;
}
