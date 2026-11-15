package com.sacco.share.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class ShareSummaryResponse {
    private Long memberId;
    private String memberName;
    private String membershipNo;
    private Integer totalShares;
    private BigDecimal totalValue;
    private boolean minimumSharesMet;
    private List<ShareHoldingResponse> holdings;
}
