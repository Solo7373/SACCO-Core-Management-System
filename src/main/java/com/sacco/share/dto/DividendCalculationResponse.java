package com.sacco.share.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class DividendCalculationResponse {
    private BigDecimal ratePercent;
    private BigDecimal totalShareValue;
    private BigDecimal totalDividend;
    private List<MemberDividendResponse> members;
}
