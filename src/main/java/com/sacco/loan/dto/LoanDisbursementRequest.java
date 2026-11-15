package com.sacco.loan.dto;

import lombok.Data;

@Data
public class LoanDisbursementRequest {
    private Long disbursementAccountId;
    private String notes;
}
