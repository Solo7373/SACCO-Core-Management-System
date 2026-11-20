package com.sacco.loan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One reminder dispatch attempt for a loan instalment trigger, scoped to a
 * single channel and a single send date. The unique constraint
 * (loan_id, schedule_id, days_before, channel, send_date) makes the
 * scheduled engine idempotent: a trigger that has already gone out once is
 * never dispatched again, even after an application restart.
 */
@Entity
@Table(name = "loan_reminders",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_loan_reminders_once",
                columnNames = {"loan_id", "schedule_id", "days_before", "channel", "send_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reminder_id")
    private Long reminderId;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "installment_no", nullable = false)
    private Integer installmentNo;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    /** Days before the due date the reminder was scheduled for, e.g. 5 for five days out. */
    @Column(name = "days_before", nullable = false)
    private Integer daysBefore;

    /** The calendar date the reminder was actually sent or attempted on. */
    @Column(name = "send_date", nullable = false)
    private LocalDate sendDate;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;

    @Column(name = "recipient", nullable = false, length = 20)
    private String recipient;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "loan_number", nullable = false, length = 32)
    private String loanNumber;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Column(name = "rendered_message", nullable = false, columnDefinition = "TEXT")
    private String renderedMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ReminderStatus status = ReminderStatus.PENDING;

    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum ReminderStatus {
        PENDING,
        SENT,
        FAILED,
        SKIPPED,
        SUPPRESSED
    }
}
