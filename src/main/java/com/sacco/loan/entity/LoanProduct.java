package com.sacco.loan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "loan_products")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanProduct {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "interest_rate", nullable = false, precision = 6, scale = 3)
    private BigDecimal interestRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_method", nullable = false)
    @Builder.Default
    private InterestMethod interestMethod = InterestMethod.REDUCING_BALANCE;

    @Column(name = "max_term_months", nullable = false)
    private Integer maxTermMonths;

    @Column(name = "min_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal minAmount = BigDecimal.ZERO;

    @Column(name = "max_amount", precision = 15, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "max_multiplier", nullable = false, precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal maxMultiplier = new BigDecimal("3.00");

    @Column(name = "penalty_rate", nullable = false, precision = 6, scale = 3)
    @Builder.Default
    private BigDecimal penaltyRate = BigDecimal.ZERO;

    @Column(name = "requires_guarantor", nullable = false)
    @Builder.Default
    private Boolean requiresGuarantor = false;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum InterestMethod { FLAT, REDUCING_BALANCE }
}
