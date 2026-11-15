package com.sacco.finance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class TaxBreakdownResponse {
    private List<TaxBreakdownRow> rows;
    private BigDecimal totalTaxableAmount;
    private BigDecimal totalTaxCollected;
}
