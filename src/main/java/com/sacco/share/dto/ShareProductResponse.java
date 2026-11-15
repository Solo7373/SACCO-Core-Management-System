package com.sacco.share.dto;

import com.sacco.share.entity.ShareProduct;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ShareProductResponse {
    private Long productId;
    private String name;
    private String description;
    private Integer minShares;
    private BigDecimal pricePerShare;
    private String status;
    private LocalDateTime createdAt;

    public static ShareProductResponse fromEntity(ShareProduct product) {
        return ShareProductResponse.builder()
                .productId(product.getProductId())
                .name(product.getName())
                .description(product.getDescription())
                .minShares(product.getMinShares())
                .pricePerShare(product.getPricePerShare())
                .status(product.getStatus())
                .createdAt(product.getCreatedAt())
                .build();
    }
}
