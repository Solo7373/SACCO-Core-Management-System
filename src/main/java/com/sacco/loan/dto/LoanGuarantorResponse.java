package com.sacco.loan.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class LoanGuarantorResponse {
    private Long guarantorId;
    private Long memberId;
    private String memberName;
    private BigDecimal guaranteedAmount;
    private String status;
}
