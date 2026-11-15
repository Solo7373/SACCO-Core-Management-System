package com.sacco.ledger.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chart_of_accounts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChartOfAccounts {
    @Id
    @Column(name = "account_code", length = 20)
    private String accountCode;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private AccountCategory category;

    @Column(name = "parent_code", length = 20)
    private String parentCode;

    @Column(name = "is_bank_account", nullable = false)
    @Builder.Default
    private Boolean isBankAccount = false;

    @Column(name = "is_cash_account", nullable = false)
    @Builder.Default
    private Boolean isCashAccount = false;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "UGX";

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum AccountCategory { ASSET, LIABILITY, EQUITY, INCOME, EXPENSE }
}
