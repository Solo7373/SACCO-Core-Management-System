package com.sacco.share.dto;

import com.sacco.share.entity.ShareTransaction;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ShareTransactionRequest {
    @NotNull(message = "Member ID is required")
    private Long memberId;

    @NotNull(message = "Product ID is required")
    private Long productId;

    @NotNull(message = "Quantity is required")
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;

    @NotNull(message = "Amount is required")
    @Min(value = 0, message = "Amount cannot be negative")
    private BigDecimal amount;

    @NotNull(message = "Transaction type is required")
    private ShareTransaction.ShareTransactionType transactionType;

    private String reference;
}
