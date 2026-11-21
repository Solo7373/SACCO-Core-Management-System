package com.sacco.finance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class TransactionSheetResponse {
    private List<TransactionSheetRow> rows;
    private int count;
    private BigDecimal totalGross;
    private BigDecimal totalNet;
}
