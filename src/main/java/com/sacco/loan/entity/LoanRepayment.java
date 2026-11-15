package com.sacco.loan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "loan_repayments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanRepayment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "repayment_id")
    private Long repaymentId;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "reference", nullable = false, unique = true, length = 40)
    private String reference;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "principal_portion", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal principalPortion = BigDecimal.ZERO;

    @Column(name = "interest_portion", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal interestPortion = BigDecimal.ZERO;

    @Column(name = "penalty_portion", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal penaltyPortion = BigDecimal.ZERO;

    @Column(name = "payment_date", nullable = false)
    @Builder.Default
    private LocalDate paymentDate = LocalDate.now();

    @Column(name = "notes")
    private String notes;

    @Column(name = "posted_by")
    private Long postedBy;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "COMPLETED";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
