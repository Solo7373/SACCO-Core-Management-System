package com.sacco.inventory.dto;

import com.sacco.inventory.entity.ShippingMethod;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ShippingMethodResponse {
    private Long methodId;
    private String code;
    private String name;
    private BigDecimal baseFee;
    private Integer estimatedDays;
    private Boolean isActive;

    public static ShippingMethodResponse fromEntity(ShippingMethod method) {
        return ShippingMethodResponse.builder()
                .methodId(method.getMethodId())
                .code(method.getCode())
                .name(method.getName())
                .baseFee(method.getBaseFee())
                .estimatedDays(method.getEstimatedDays())
                .isActive(method.getIsActive())
                .build();
    }
}
