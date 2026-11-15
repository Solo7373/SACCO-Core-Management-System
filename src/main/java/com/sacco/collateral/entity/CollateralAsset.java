package com.sacco.collateral.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "collateral_assets")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CollateralAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "collateral_id")
    private Long collateralId;

    @Column(name = "asset_code", nullable = false, unique = true, length = 32)
    private String assetCode;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "loan_account_id")
    private Long loanAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 40)
    private AssetType assetType;

    @Column(name = "title_number", nullable = false, length = 200)
    private String titleNumber;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "estimated_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal estimatedValue;

    @Column(name = "forced_sale_value", precision = 15, scale = 2)
    private BigDecimal forcedSaleValue;

    @Column(name = "valuation_date")
    private LocalDate valuationDate;

    @Column(name = "valuer_name", length = 200)
    private String valuerName;

    @Column(name = "registered_owner_name", length = 200)
    private String registeredOwnerName;

    @Column(name = "custodian_location", length = 300)
    private String custodianLocation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private CollateralStatus status = CollateralStatus.HELD_IN_CUSTODY;

    @Column(name = "registered_by")
    private Long registeredBy;

    @CreationTimestamp
    @Column(name = "registered_at", nullable = false, updatable = false)
    private LocalDateTime registeredAt;

    @Column(name = "released_to", length = 200)
    private String releasedTo;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    @Column(name = "release_authorized_by", length = 200)
    private String releaseAuthorizedBy;

    @Column(name = "release_authorized_at")
    private LocalDateTime releaseAuthorizedAt;

    @Column(name = "release_signature", length = 500)
    private String releaseSignature;

    @Column(name = "release_notes", columnDefinition = "TEXT")
    private String releaseNotes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum AssetType {
        LAND_TITLE,
        RESIDENTIAL_HOUSE,
        COMMERCIAL_BUILDING,
        MOTOR_VEHICLE,
        EQUIPMENT,
        OTHER
    }

    public enum CollateralStatus {
        HELD_IN_CUSTODY,
        RELEASED,
        UNDER_DEFAULT_AUCTION
    }
}
