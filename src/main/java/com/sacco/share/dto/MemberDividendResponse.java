package com.sacco.share.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MemberDividendResponse {
    private Long memberId;
    private String memberName;
    private String membershipNo;
    private Integer totalShares;
    private BigDecimal shareValue;
    private BigDecimal dividend;
}
