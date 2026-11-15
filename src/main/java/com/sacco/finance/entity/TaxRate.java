package com.sacco.finance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tax_rates")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaxRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tax_rate_id")
    private Long taxRateId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "code", nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Column(name = "rate_percent", nullable = false, precision = 8, scale = 4)
    private BigDecimal ratePercent;

    @Column(name = "applies_to_fees", nullable = false)
    @Builder.Default
    private Boolean appliesToFees = true;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "effective_from", nullable = false)
    @Builder.Default
    private LocalDate effectiveFrom = LocalDate.now();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
