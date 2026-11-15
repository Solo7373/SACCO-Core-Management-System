package com.sacco.loan.dto;

import com.sacco.loan.entity.LoanProduct;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class LoanProductResponse {
    private Long productId;
    private String name;
    private String description;
    private BigDecimal interestRate;
    private LoanProduct.InterestMethod interestMethod;
    private Integer maxTermMonths;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private BigDecimal maxMultiplier;
    private BigDecimal penaltyRate;
    private Boolean requiresGuarantor;
    private String status;
    private LocalDateTime createdAt;

    public static LoanProductResponse fromEntity(LoanProduct product) {
        return LoanProductResponse.builder()
                .productId(product.getProductId())
                .name(product.getName())
                .description(product.getDescription())
                .interestRate(product.getInterestRate())
                .interestMethod(product.getInterestMethod())
                .maxTermMonths(product.getMaxTermMonths())
                .minAmount(product.getMinAmount())
                .maxAmount(product.getMaxAmount())
                .maxMultiplier(product.getMaxMultiplier())
                .penaltyRate(product.getPenaltyRate())
                .requiresGuarantor(product.getRequiresGuarantor())
                .status(product.getStatus())
                .createdAt(product.getCreatedAt())
                .build();
    }
}
