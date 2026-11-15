package com.sacco.savings.dto;

import com.sacco.savings.entity.SavingsTransaction;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class SavingsTransactionResponse {
    private Long transactionId;
    private Long accountId;
    private String reference;
    private SavingsTransaction.SavingsTransactionType transactionType;
    private BigDecimal amount;
    private String currency;
    private SavingsTransaction.SavingsTransactionStatus status;
    private String externalReference;
    private String authorizedBy;
    private String approvalReference;
    private LocalDate valueDate;
    private String description;
    private String channel;
    private BigDecimal feeAmount;
    private BigDecimal taxAmount;
    private BigDecimal netAmount;
    private Long postedBy;
    private LocalDateTime createdAt;

    public static SavingsTransactionResponse fromEntity(SavingsTransaction tx) {
        return SavingsTransactionResponse.builder()
                .transactionId(tx.getTransactionId())
                .accountId(tx.getAccountId())
                .reference(tx.getReference())
                .transactionType(tx.getTransactionType())
                .amount(tx.getAmount())
                .currency(tx.getCurrency())
                .status(tx.getStatus())
                .externalReference(tx.getExternalReference())
                .authorizedBy(tx.getAuthorizedBy())
                .approvalReference(tx.getApprovalReference())
                .valueDate(tx.getValueDate())
                .description(tx.getDescription())
                .channel(tx.getChannel())
                .feeAmount(tx.getFeeAmount())
                .taxAmount(tx.getTaxAmount())
                .netAmount(tx.getNetAmount())
                .postedBy(tx.getPostedBy())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
