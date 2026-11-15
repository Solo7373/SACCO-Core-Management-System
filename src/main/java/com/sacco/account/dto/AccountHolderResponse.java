package com.sacco.account.dto;

import com.sacco.account.entity.AccountHolder;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class AccountHolderResponse {
    private Long accountId;
    private Long memberId;
    private AccountHolder.HolderType holderType;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    private String approvalReference;
    private LocalDateTime createdAt;

    public static AccountHolderResponse fromEntity(AccountHolder holder) {
        return AccountHolderResponse.builder()
                .accountId(holder.getAccountId())
                .memberId(holder.getMemberId())
                .holderType(holder.getHolderType())
                .effectiveFrom(holder.getEffectiveFrom())
                .effectiveTo(holder.getEffectiveTo())
                .approvalReference(holder.getApprovalReference())
                .createdAt(holder.getCreatedAt())
                .build();
    }
}
