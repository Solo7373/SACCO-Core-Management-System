package com.sacco.share.dto;

import com.sacco.share.entity.ShareTransaction;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class ShareTransactionResponse {
    private Long transactionId;
    private Long memberId;
    private Long productId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private ShareTransaction.ShareTransactionType transactionType;
    private ShareTransaction.ShareTransactionStatus status;
    private String reference;
    private LocalDate transactionDate;
    private Long processedBy;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;

    public static ShareTransactionResponse fromEntity(ShareTransaction tx) {
        return ShareTransactionResponse.builder()
                .transactionId(tx.getTransactionId())
                .memberId(tx.getMemberId())
                .productId(tx.getProductId())
                .quantity(tx.getQuantity())
                .unitPrice(tx.getUnitPrice())
                .amount(tx.getAmount())
                .transactionType(tx.getTransactionType())
                .status(tx.getStatus())
                .reference(tx.getReference())
                .transactionDate(tx.getTransactionDate())
                .processedBy(tx.getProcessedBy())
                .processedAt(tx.getProcessedAt())
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
