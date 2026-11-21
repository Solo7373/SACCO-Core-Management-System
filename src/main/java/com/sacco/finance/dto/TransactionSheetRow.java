package com.sacco.finance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class TransactionSheetRow {
    private LocalDate date;
    private String reference;
    private String source;
    private String transactionType;
    private String channel;
    private Long memberId;
    private String memberName;
    private BigDecimal grossAmount;
    private BigDecimal netAmount;
    private String description;
}
