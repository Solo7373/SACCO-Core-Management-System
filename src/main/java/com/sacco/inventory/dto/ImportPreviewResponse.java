package com.sacco.inventory.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ImportPreviewResponse {
    private BigDecimal baseCost;
    private BigDecimal shippingFee;
    private BigDecimal customsDutyRate;
    private BigDecimal customsDutyAmount;
    private BigDecimal vatRate;
    private BigDecimal vatAmount;
    private BigDecimal otherTaxes;
    private BigDecimal totalLandedCost;
    private BigDecimal unitLandedCost;
}
