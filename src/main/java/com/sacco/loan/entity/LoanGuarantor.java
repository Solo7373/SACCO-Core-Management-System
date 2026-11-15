package com.sacco.loan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "loan_guarantors")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanGuarantor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "guarantor_id")
    private Long guarantorId;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "guaranteed_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal guaranteedAmount;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "PLEDGED";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
