package com.sacco.account.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "account_holders")
@IdClass(AccountHolderId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AccountHolder {
    @Id
    @Column(name = "account_id")
    private Long accountId;

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", insertable = false, updatable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "holder_type", nullable = false)
    @Builder.Default
    private HolderType holderType = HolderType.PRIMARY;

    @Column(name = "effective_from", nullable = false)
    @Builder.Default
    private LocalDate effectiveFrom = LocalDate.now();

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "approval_reference", length = 200)
    private String approvalReference;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum HolderType { PRIMARY, JOINT, AUTHORIZED }
}
