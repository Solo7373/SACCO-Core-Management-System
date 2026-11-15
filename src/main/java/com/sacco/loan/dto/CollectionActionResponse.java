package com.sacco.loan.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class CollectionActionResponse {
    private Long actionId;
    private Long loanId;
    private Long demandId;
    private String actionType;
    private String outcome;
    private BigDecimal promisedAmount;
    private LocalDate promisedDate;
    private String notes;
    private Long actorId;
    private String actorName;
    private LocalDateTime createdAt;
}
