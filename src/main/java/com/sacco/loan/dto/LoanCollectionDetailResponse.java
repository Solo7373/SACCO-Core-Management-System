package com.sacco.loan.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LoanCollectionDetailResponse {
    private CollectionLoanRow loan;
    private List<LoanScheduleResponse> schedule;
    private List<LoanDemandResponse> demands;
    private List<CollectionActionResponse> actions;
    private List<LoanRepaymentResponse> repayments;
    private List<LoanGuarantorResponse> guarantors;
}
