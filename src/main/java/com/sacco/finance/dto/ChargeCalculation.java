package com.sacco.finance.dto;

import java.math.BigDecimal;

/**
 * Immutable result of applying the active fee and tax configuration to a
 * transaction amount.
 */
public record ChargeCalculation(
        BigDecimal feeAmount,
        BigDecimal taxAmount,
        BigDecimal netAmount,
        Long feeConfigId,
        String feeName,
        Long taxRateId,
        BigDecimal taxRatePercent,
        String taxRateName
) {
}
