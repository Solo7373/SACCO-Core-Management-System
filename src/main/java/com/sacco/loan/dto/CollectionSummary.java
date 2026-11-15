package com.sacco.loan.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class CollectionSummary {
    private LocalDate asOf;
    private int activeLoans;
    private int delinquentLoans;
    private BigDecimal totalOutstanding;
    private BigDecimal totalArrears;
    private BigDecimal totalPenalty;
    private BigDecimal parPercent;
    private long promisesDue;
    private long promisesOverdue;
    private long openDemands;
    private long demandsThisMonth;
    private BigDecimal recoveryThisMonth;
    private List<AgingBucketResponse> buckets;
}
