package com.sacco.report.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class IncomeExpenditureResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private List<FinancialLineResponse> income;
    private BigDecimal totalIncome;
    private List<FinancialLineResponse> expenses;
    private BigDecimal totalExpenses;
    private BigDecimal netSurplus;
}
