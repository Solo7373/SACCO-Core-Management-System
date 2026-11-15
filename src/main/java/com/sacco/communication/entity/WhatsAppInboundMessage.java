package com.sacco.communication.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A message a member sent to the SACCO's WhatsApp number. Stored verbatim
 * (bounded by the webhook parser) so staff can see the conversation and so a
 * later reply can be matched to the customer service window.
 */
@Entity
@Table(name = "whatsapp_inbound_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppInboundMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inbound_id")
    private Long inboundId;

    @Column(name = "provider_message_id", nullable = false, unique = true, length = 128)
    private String providerMessageId;

    @Column(name = "from_number", nullable = false, length = 20)
    private String fromNumber;

    @Column(name = "contact_name", length = 255)
    private String contactName;

    @Column(name = "message_type", length = 30)
    private String messageType;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "media_id", length = 128)
    private String mediaId;

    @Column(name = "media_mime_type", length = 100)
    private String mediaMimeType;

    @Column(name = "context_message_id", length = 128)
    private String contextMessageId;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "processed", nullable = false)
    @Builder.Default
    private boolean processed = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
