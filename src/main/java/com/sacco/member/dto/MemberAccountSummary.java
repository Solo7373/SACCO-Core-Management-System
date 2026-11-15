package com.sacco.member.dto;

import com.sacco.account.entity.Account;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MemberAccountSummary {
    private Long accountId;
    private String accountNumber;
    private Account.AccountType accountType;
    private String currency;
    private Account.AccountStatus status;
    private BigDecimal balance;
}
