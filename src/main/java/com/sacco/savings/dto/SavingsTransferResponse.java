package com.sacco.savings.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SavingsTransferResponse {
    private String reference;
    private SavingsTransactionResponse fromTransaction;
    private SavingsTransactionResponse toTransaction;
}
