package com.sacco.loan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One contact or follow-up attempt in the collections trail: a phone call, a
 * home visit, a promise-to-pay, a guarantor contact or a legal referral.
 */
@Entity
@Table(name = "loan_collection_actions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LoanCollectionAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "action_id")
    private Long actionId;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "demand_id")
    private Long demandId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private Outcome outcome;

    @Column(name = "promised_amount", precision = 15, scale = 2)
    private BigDecimal promisedAmount;

    @Column(name = "promised_date")
    private LocalDate promisedDate;

    @Column(name = "notes")
    private String notes;

    @Column(name = "actor_id")
    private Long actorId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum ActionType {
        PHONE_CALL,
        SMS,
        EMAIL,
        HOME_VISIT,
        OFFICE_MEETING,
        GUARANTOR_CONTACT,
        DEMAND_ISSUED,
        PROMISE_TO_PAY,
        LEGAL_REFERRAL,
        OTHER
    }

    public enum Outcome {
        CONTACTED,
        NO_ANSWER,
        PROMISE_TO_PAY,
        PAID,
        DISPUTED,
        ESCALATED,
        NOTE
    }
}
