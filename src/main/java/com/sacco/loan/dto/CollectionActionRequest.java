package com.sacco.loan.dto;

import com.sacco.loan.entity.LoanCollectionAction;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CollectionActionRequest {
    @NotNull(message = "Action type is required")
    private LoanCollectionAction.ActionType actionType;

    @NotNull(message = "Outcome is required")
    private LoanCollectionAction.Outcome outcome;

    private BigDecimal promisedAmount;
    private LocalDate promisedDate;
    private Long demandId;
    private String notes;
}
