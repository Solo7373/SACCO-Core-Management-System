package com.sacco.collateral.dto;

import com.sacco.account.entity.Account;
import com.sacco.collateral.entity.CollateralAsset;
import com.sacco.member.entity.Member;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class CollateralResponse {
    private Long collateralId;
    private String assetCode;
    private Long memberId;
    private String memberName;
    private String membershipNo;
    private Long loanAccountId;
    private String loanAccountNumber;
    private CollateralAsset.AssetType assetType;
    private String titleNumber;
    private String description;
    private BigDecimal estimatedValue;
    private BigDecimal forcedSaleValue;
    private LocalDate valuationDate;
    private String valuerName;
    private String registeredOwnerName;
    private String custodianLocation;
    private CollateralAsset.CollateralStatus status;
    private Long registeredBy;
    private LocalDateTime registeredAt;
    private String releasedTo;
    private LocalDate releaseDate;
    private String releaseAuthorizedBy;
    private LocalDateTime releaseAuthorizedAt;
    private String releaseSignature;
    private String releaseNotes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<CollateralDocumentResponse> documents;
    private List<CollateralStatusHistoryResponse> history;

    public static CollateralResponse fromEntity(CollateralAsset asset, Member member, Account loanAccount) {
        String memberName = member == null
                ? null
                : (member.getFirstName() + " " + member.getLastName()).trim();

        return CollateralResponse.builder()
                .collateralId(asset.getCollateralId())
                .assetCode(asset.getAssetCode())
                .memberId(asset.getMemberId())
                .memberName(memberName)
                .membershipNo(member == null ? null : member.getMembershipNo())
                .loanAccountId(asset.getLoanAccountId())
                .loanAccountNumber(loanAccount == null ? null : loanAccount.getAccountNumber())
                .assetType(asset.getAssetType())
                .titleNumber(asset.getTitleNumber())
                .description(asset.getDescription())
                .estimatedValue(asset.getEstimatedValue())
                .forcedSaleValue(asset.getForcedSaleValue())
                .valuationDate(asset.getValuationDate())
                .valuerName(asset.getValuerName())
                .registeredOwnerName(asset.getRegisteredOwnerName())
                .custodianLocation(asset.getCustodianLocation())
                .status(asset.getStatus())
                .registeredBy(asset.getRegisteredBy())
                .registeredAt(asset.getRegisteredAt())
                .releasedTo(asset.getReleasedTo())
                .releaseDate(asset.getReleaseDate())
                .releaseAuthorizedBy(asset.getReleaseAuthorizedBy())
                .releaseAuthorizedAt(asset.getReleaseAuthorizedAt())
                .releaseSignature(asset.getReleaseSignature())
                .releaseNotes(asset.getReleaseNotes())
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .documents(List.of())
                .history(List.of())
                .build();
    }
}
