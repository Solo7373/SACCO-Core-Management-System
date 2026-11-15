package com.sacco.account.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "accounts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "account_number", nullable = false, unique = true, length = 32)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "UGX";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AccountStatus status = AccountStatus.PENDING;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    @Column(name = "dormant_since")
    private LocalDateTime dormantSince;

    @Column(name = "fixed_deposit_maturity_date")
    private LocalDate fixedDepositMaturityDate;

    @Column(name = "fixed_deposit_interest_rate", precision = 5, scale = 2)
    private BigDecimal fixedDepositInterestRate;

    @Column(name = "fixed_deposit_term_months")
    private Integer fixedDepositTermMonths;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AccountHolder> holders = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "opened_at", nullable = false, updatable = false)
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum AccountType { SAVINGS, SHARE_CAPITAL, LOAN_DISBURSEMENT, FIXED_DEPOSIT }
    public enum AccountStatus { PENDING, ACTIVE, FROZEN, DORMANT, CLOSED }
}
