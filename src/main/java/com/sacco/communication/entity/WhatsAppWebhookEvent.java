package com.sacco.communication.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Durable record of every status callback. Meta delivers webhooks at least
 * once and may deliver a status before the outbox has stored the provider
 * message id, so unmatched events remain unprocessed for reconciliation.
 */
@Entity
@Table(name = "whatsapp_webhook_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    /** Stable de-duplication key, for example status:wamid:delivered:1700000000. */
    @Column(name = "event_key", nullable = false, unique = true, length = 200)
    private String eventKey;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "provider_message_id", length = 128)
    private String providerMessageId;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "processed", nullable = false)
    @Builder.Default
    private boolean processed = false;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
