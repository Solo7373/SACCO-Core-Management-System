package com.sacco.collateral.dto;

import com.sacco.collateral.entity.CollateralAsset;
import com.sacco.collateral.entity.CollateralStatusHistory;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CollateralStatusHistoryResponse {
    private Long historyId;
    private Long collateralId;
    private CollateralAsset.CollateralStatus fromStatus;
    private CollateralAsset.CollateralStatus toStatus;
    private String action;
    private String notes;
    private Long actorId;
    private String authorizedBy;
    private String signature;
    private LocalDateTime createdAt;

    public static CollateralStatusHistoryResponse fromEntity(CollateralStatusHistory history) {
        return CollateralStatusHistoryResponse.builder()
                .historyId(history.getHistoryId())
                .collateralId(history.getCollateralId())
                .fromStatus(history.getFromStatus())
                .toStatus(history.getToStatus())
                .action(history.getAction())
                .notes(history.getNotes())
                .actorId(history.getActorId())
                .authorizedBy(history.getAuthorizedBy())
                .signature(history.getSignature())
                .createdAt(history.getCreatedAt())
                .build();
    }
}
