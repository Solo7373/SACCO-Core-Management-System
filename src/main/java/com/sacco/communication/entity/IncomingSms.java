package com.sacco.communication.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Incoming SMS callback, including STOP/START opt-out keywords. */
@Entity
@Table(name = "incoming_sms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncomingSms {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "incoming_id")
    private Long incomingId;

    /** Africa's Talking message id. Unique, so duplicate callbacks are ignored. */
    @Column(name = "provider_message_id", nullable = false, unique = true, length = 100)
    private String providerMessageId;

    @Column(name = "sender", length = 30)
    private String sender;

    @Column(name = "recipient", length = 30)
    private String recipient;

    @Column(name = "text", columnDefinition = "TEXT")
    private String text;

    @Column(name = "link_id", length = 100)
    private String linkId;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private LocalDateTime receivedAt = LocalDateTime.now();

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "processed", nullable = false)
    @Builder.Default
    private boolean processed = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
