package com.sacco.finance.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction_charges")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TransactionCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "charge_id")
    private Long chargeId;

    @Column(name = "reference", nullable = false, length = 40)
    private String reference;

    @Column(name = "transaction_type", nullable = false, length = 30)
    private String transactionType;

    @Column(name = "channel", nullable = false, length = 30)
    private String channel;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "gross_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "fee_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal feeAmount = BigDecimal.ZERO;

    @Column(name = "tax_amount", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "net_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "fee_config_id")
    private Long feeConfigId;

    @Column(name = "tax_rate_id")
    private Long taxRateId;

    @Column(name = "tax_rate_percent", precision = 8, scale = 4)
    private BigDecimal taxRatePercent;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "posted_by")
    private Long postedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
