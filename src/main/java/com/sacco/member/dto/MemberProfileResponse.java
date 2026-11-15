package com.sacco.member.dto;

import com.sacco.collateral.dto.CollateralResponse;
import com.sacco.share.dto.ShareTransactionResponse;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class MemberProfileResponse {
    private MemberResponse member;
    private List<IdentityDocumentResponse> documents;
    private List<MemberAccountSummary> accounts;
    private List<ShareTransactionResponse> shareTransactions;
    private List<CollateralResponse> collateral;
    private BigDecimal totalSavings;
    private BigDecimal totalShareCapital;
}
