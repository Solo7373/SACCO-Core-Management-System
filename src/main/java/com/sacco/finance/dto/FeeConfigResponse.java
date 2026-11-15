package com.sacco.finance.dto;

import com.sacco.finance.entity.FeeConfig;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class FeeConfigResponse {
    private Long feeConfigId;
    private String name;
    private String transactionType;
    private String channel;
    private FeeConfig.FeeType feeType;
    private BigDecimal feeValue;
    private BigDecimal minAmount;
    private BigDecimal maxFee;
    private Long taxRateId;
    private String taxRateName;
    private BigDecimal taxRatePercent;
    private Boolean isActive;
    private String description;
    private LocalDateTime createdAt;

    public static FeeConfigResponse fromEntity(FeeConfig config) {
        return FeeConfigResponse.builder()
                .feeConfigId(config.getFeeConfigId())
                .name(config.getName())
                .transactionType(config.getTransactionType())
                .channel(config.getChannel())
                .feeType(config.getFeeType())
                .feeValue(config.getFeeValue())
                .minAmount(config.getMinAmount())
                .maxFee(config.getMaxFee())
                .taxRateId(config.getTaxRateId())
                .isActive(config.getIsActive())
                .description(config.getDescription())
                .createdAt(config.getCreatedAt())
                .build();
    }
}
