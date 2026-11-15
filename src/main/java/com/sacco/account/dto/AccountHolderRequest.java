package com.sacco.account.dto;

import com.sacco.account.entity.AccountHolder;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AccountHolderRequest {
    @NotNull(message = "Member ID is required")
    private Long memberId;

    @NotNull(message = "Holder type is required")
    private AccountHolder.HolderType holderType;

    private LocalDate effectiveFrom;
    private String approvalReference;
}
