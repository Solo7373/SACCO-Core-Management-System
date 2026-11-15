package com.sacco.inventory.dto;

import com.sacco.inventory.entity.ProductImport;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ImportResponse {
    private Long importId;
    private Long productId;
    private String productName;
    private String productCode;
    private String supplierName;
    private String supplierCountry;
    private Integer quantity;
    private BigDecimal unitCost;
    private String shippingMethod;
    private BigDecimal shippingFee;
    private BigDecimal customsDutyRate;
    private BigDecimal customsDutyAmount;
    private BigDecimal vatRate;
    private BigDecimal vatAmount;
    private BigDecimal otherTaxes;
    private BigDecimal totalLandedCost;
    private ProductImport.ImportStatus status;
    private LocalDate orderDate;
    private LocalDate expectedDate;
    private LocalDate receivedDate;
    private String notes;
    private Long createdBy;
    private LocalDateTime createdAt;

    public static ImportResponse fromEntity(ProductImport item) {
        return ImportResponse.builder()
                .importId(item.getImportId())
                .productId(item.getProductId())
                .supplierName(item.getSupplierName())
                .supplierCountry(item.getSupplierCountry())
                .quantity(item.getQuantity())
                .unitCost(item.getUnitCost())
                .shippingMethod(item.getShippingMethod())
                .shippingFee(item.getShippingFee())
                .customsDutyRate(item.getCustomsDutyRate())
                .customsDutyAmount(item.getCustomsDutyAmount())
                .vatRate(item.getVatRate())
                .vatAmount(item.getVatAmount())
                .otherTaxes(item.getOtherTaxes())
                .totalLandedCost(item.getTotalLandedCost())
                .status(item.getStatus())
                .orderDate(item.getOrderDate())
                .expectedDate(item.getExpectedDate())
                .receivedDate(item.getReceivedDate())
                .notes(item.getNotes())
                .createdBy(item.getCreatedBy())
                .createdAt(item.getCreatedAt())
                .build();
    }
}
