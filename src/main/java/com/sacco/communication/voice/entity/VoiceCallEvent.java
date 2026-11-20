package com.sacco.communication.voice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Raw asynchronous callback event delivered by Africa's Talking for a voice
 * call. Callbacks are stored verbatim (bounded by the webhook payload parser)
 * so the call timeline can be audited even when the originating call row was
 * reconciled out of order.
 */
@Entity
@Table(name = "voice_call_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceCallEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "call_id", nullable = false)
    private Long callId;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private VoiceCall.VoiceCallStatus status;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "cost", length = 40)
    private String cost;

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
