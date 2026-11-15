package com.sacco.communication.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Outgoing SMS outbox row. A message is persisted as QUEUED and delivered by
 * the scheduled dispatcher. Delivery reports advance the status monotonically
 * (a late "Sent" callback can never overwrite "Delivered").
 */
@Entity
@Table(name = "sms_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmsMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    /** Public, non-guessable reference. Useful for idempotent client retries. */
    @Column(name = "reference", nullable = false, unique = true, length = 36)
    private String reference;

    /** Groups the rows produced by one bulk request. */
    @Column(name = "batch_id", length = 36)
    private String batchId;

    @Column(name = "recipient", nullable = false, length = 20)
    private String recipient;

    @Column(name = "sender_id", length = 20)
    private String senderId;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 30)
    @Builder.Default
    private SmsMessageType messageType = SmsMessageType.NOTIFICATION;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private SmsStatus status = SmsStatus.QUEUED;

    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(name = "provider_status", length = 40)
    private String providerStatus;

    @Column(name = "provider_status_code")
    private Integer providerStatusCode;

    @Column(name = "provider_cost", length = 40)
    private String providerCost;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private int maxAttempts = 5;

    @Column(name = "next_attempt_at", nullable = false)
    @Builder.Default
    private LocalDateTime nextAttemptAt = LocalDateTime.now();

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    /** Optional member link for member-facing message history. */
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "created_by")
    private Long createdBy;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum SmsStatus {
        QUEUED,
        SENDING,
        SENT,
        DELIVERED,
        FAILED,
        REJECTED,
        EXPIRED
    }

    public enum SmsMessageType {
        OTP,
        NOTIFICATION,
        ALERT,
        COLLECTION,
        TRANSACTION,
        MARKETING,
        TEST
    }
}
