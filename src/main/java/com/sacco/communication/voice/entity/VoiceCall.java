package com.sacco.communication.voice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * One outbound Africa Talking voice call, either triggered manually by a
 * collections officer or by the automated loan reminder engine.
 */
@Entity
@Table(name = "voice_calls")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceCall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "call_id")
    private Long callId;

    @Column(name = "loan_id")
    private Long loanId;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "to_phone", nullable = false, length = 20)
    private String toPhone;

    @Column(name = "from_caller_id", length = 50)
    private String fromCallerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private VoiceCallStatus status = VoiceCallStatus.QUEUED;

    @Column(name = "provider_call_id", length = 100)
    private String providerCallId;

    @Column(name = "provider_status", length = 40)
    private String providerStatus;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "cost", length = 40)
    private String cost;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "initiated_at", nullable = false)
    @Builder.Default
    private LocalDateTime initiatedAt = LocalDateTime.now();

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum VoiceCallStatus {
        QUEUED,
        RINGING,
        ANSWERED,
        COMPLETED,
        NO_ANSWER,
        FAILED,
        CANCELLED
    }
}
