package com.sacco.loan.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class AgingBucketResponse {
    private String bucket;
    private String label;
    private long loanCount;
    private BigDecimal amount;
}
