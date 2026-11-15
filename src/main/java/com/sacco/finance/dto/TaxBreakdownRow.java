package com.sacco.finance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class TaxBreakdownRow {
    private String taxName;
    private String taxCode;
    private String category;
    private BigDecimal ratePercent;
    private BigDecimal taxableAmount;
    private BigDecimal taxCollected;
    private long transactionCount;
}
