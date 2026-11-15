package com.sacco.loan.dto;

import com.sacco.loan.entity.LoanDemand;
import lombok.Data;

import java.time.LocalDate;

@Data
public class LoanDemandRequest {
    private LoanDemand.DemandStage stage;
    private LocalDate dueDate;
    private LoanDemand.DeliveryMethod deliveryMethod;
    private String notes;
}
