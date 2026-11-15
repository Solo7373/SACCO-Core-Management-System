package com.sacco.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlySavingsPoint {
    private String month;
    private BigDecimal deposits;
    private BigDecimal withdrawals;
    private BigDecimal net;
    private BigDecimal cumulative;
    /** Savings balance immediately before the month opened. */
    private BigDecimal open;
    /** Highest intra-month running balance. */
    private BigDecimal high;
    /** Lowest intra-month running balance after withdrawals. */
    private BigDecimal low;
    /** Savings balance at month end (same as cumulative). */
    private BigDecimal close;
}
