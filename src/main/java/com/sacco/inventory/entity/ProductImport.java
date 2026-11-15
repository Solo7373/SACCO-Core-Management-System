package com.sacco.inventory.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_imports")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProductImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "import_id")
    private Long importId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "supplier_name", nullable = false, length = 200)
    private String supplierName;

    @Column(name = "supplier_country", length = 100)
    private String supplierCountry;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal unitCost;

    @Column(name = "shipping_method", nullable = false, length = 30)
    private String shippingMethod;

    @Column(name = "shipping_fee", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal shippingFee = BigDecimal.ZERO;

    @Column(name = "customs_duty_rate", nullable = false, precision = 8, scale = 4)
    @Builder.Default
    private BigDecimal customsDutyRate = BigDecimal.ZERO;

    @Column(name = "customs_duty_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal customsDutyAmount = BigDecimal.ZERO;

    @Column(name = "vat_rate", nullable = false, precision = 8, scale = 4)
    @Builder.Default
    private BigDecimal vatRate = BigDecimal.ZERO;

    @Column(name = "vat_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal vatAmount = BigDecimal.ZERO;

    @Column(name = "other_taxes", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal otherTaxes = BigDecimal.ZERO;

    @Column(name = "total_landed_cost", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalLandedCost;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ImportStatus status = ImportStatus.ORDERED;

    @Column(name = "order_date")
    private LocalDate orderDate;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(name = "received_date")
    private LocalDate receivedDate;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum ImportStatus { ORDERED, IN_TRANSIT, RECEIVED, CANCELLED }
}
