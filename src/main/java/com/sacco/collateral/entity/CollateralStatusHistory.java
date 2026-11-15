package com.sacco.collateral.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "collateral_status_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CollateralStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "collateral_id", nullable = false)
    private Long collateralId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private CollateralAsset.CollateralStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private CollateralAsset.CollateralStatus toStatus;

    @Column(name = "action", nullable = false, length = 50)
    private String action;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "authorized_by", length = 200)
    private String authorizedBy;

    @Column(name = "signature", length = 500)
    private String signature;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
