package com.sacco.finance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fee_configs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FeeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fee_config_id")
    private Long feeConfigId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "transaction_type", nullable = false, length = 30)
    private String transactionType;

    @Column(name = "channel", nullable = false, length = 30)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false, length = 20)
    private FeeType feeType;

    @Column(name = "fee_value", nullable = false, precision = 15, scale = 4)
    private BigDecimal feeValue;

    @Column(name = "min_amount", precision = 15, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_fee", precision = 15, scale = 2)
    private BigDecimal maxFee;

    @Column(name = "tax_rate_id")
    private Long taxRateId;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "description", length = 255)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum FeeType { FIXED, PERCENTAGE }
}
