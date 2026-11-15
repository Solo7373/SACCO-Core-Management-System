package com.sacco.loan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A formal demand notice issued against an overdue loan. The monetary columns
 * are a snapshot taken when the notice is created; later repayments or penalty
 * accruals do not rewrite the historical letter.
 */
@Entity
@Table(name = "loan_demands")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanDemand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "demand_id")
    private Long demandId;

    @Column(name = "demand_number", nullable = false, unique = true, length = 40)
    private String demandNumber;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false)
    private DemandStage stage;

    @Column(name = "amount_demanded", nullable = false, precision = 15, scale = 2)
    private BigDecimal amountDemanded;

    @Column(name = "principal_arrears", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal principalArrears = BigDecimal.ZERO;

    @Column(name = "interest_arrears", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal interestArrears = BigDecimal.ZERO;

    @Column(name = "penalty_arrears", nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal penaltyArrears = BigDecimal.ZERO;

    @Column(name = "days_overdue", nullable = false)
    @Builder.Default
    private Integer daysOverdue = 0;

    @Column(name = "issued_by")
    private Long issuedBy;

    @Column(name = "issued_at", nullable = false)
    @Builder.Default
    private LocalDateTime issuedAt = LocalDateTime.now();

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false)
    @Builder.Default
    private DeliveryMethod deliveryMethod = DeliveryMethod.PRINT;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private DemandStatus status = DemandStatus.ISSUED;

    @Column(name = "notes")
    private String notes;

    @Column(name = "document_seal", length = 64)
    private String documentSeal;

    /**
     * JSON snapshot of the overdue installments at issuance. Reprinting the
     * demand letter uses this instead of the live schedule so a later
     * repayment never rewrites the historical notice.
     */
    @Column(name = "schedule_snapshot", columnDefinition = "TEXT")
    private String scheduleSnapshot;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum DemandStage {
        FIRST_DEMAND,
        SECOND_DEMAND,
        FINAL_DEMAND,
        GUARANTOR_DEMAND,
        LEGAL_NOTICE
    }

    public enum DeliveryMethod {
        PRINT,
        EMAIL,
        SMS,
        IN_PERSON
    }

    public enum DemandStatus {
        ISSUED,
        PARTIALLY_SETTLED,
        SETTLED,
        CANCELLED,
        ESCALATED
    }
}
