package com.sacco.account.dto;

import com.sacco.account.entity.Account;
import com.sacco.member.entity.Member;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class AccountResponse {
    private Long accountId;
    private String accountNumber;
    private Account.AccountType accountType;
    private String currency;
    private Account.AccountStatus status;
    private Long branchId;
    private Long primaryMemberId;
    private String primaryMemberName;
    private String membershipNo;
    private LocalDateTime lastActivityAt;
    private LocalDateTime dormantSince;
    private LocalDate fixedDepositMaturityDate;
    private BigDecimal fixedDepositInterestRate;
    private Integer fixedDepositTermMonths;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static AccountResponse fromEntity(Account account) {
        return fromEntity(account, null);
    }

    public static AccountResponse fromEntity(Account account, Member primaryMember) {
        return AccountResponse.builder()
                .accountId(account.getAccountId())
                .accountNumber(account.getAccountNumber())
                .accountType(account.getAccountType())
                .currency(account.getCurrency())
                .status(account.getStatus())
                .branchId(account.getBranchId())
                .primaryMemberId(primaryMember == null ? null : primaryMember.getMemberId())
                .primaryMemberName(primaryMember == null
                        ? null
                        : (primaryMember.getFirstName() + " " + primaryMember.getLastName()).trim())
                .membershipNo(primaryMember == null ? null : primaryMember.getMembershipNo())
                .lastActivityAt(account.getLastActivityAt())
                .dormantSince(account.getDormantSince())
                .fixedDepositMaturityDate(account.getFixedDepositMaturityDate())
                .fixedDepositInterestRate(account.getFixedDepositInterestRate())
                .fixedDepositTermMonths(account.getFixedDepositTermMonths())
                .openedAt(account.getOpenedAt())
                .closedAt(account.getClosedAt())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .build();
    }
}
