package com.sacco.finance.dto;

import com.sacco.finance.entity.TaxRate;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class TaxRateResponse {
    private Long taxRateId;
    private String name;
    private String code;
    private String category;
    private BigDecimal ratePercent;
    private Boolean appliesToFees;
    private Boolean isActive;
    private String description;
    private LocalDate effectiveFrom;
    private LocalDateTime createdAt;

    public static TaxRateResponse fromEntity(TaxRate rate) {
        return TaxRateResponse.builder()
                .taxRateId(rate.getTaxRateId())
                .name(rate.getName())
                .code(rate.getCode())
                .category(rate.getCategory())
                .ratePercent(rate.getRatePercent())
                .appliesToFees(rate.getAppliesToFees())
                .isActive(rate.getIsActive())
                .description(rate.getDescription())
                .effectiveFrom(rate.getEffectiveFrom())
                .createdAt(rate.getCreatedAt())
                .build();
    }
}
