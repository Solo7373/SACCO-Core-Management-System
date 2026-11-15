package com.sacco.communication.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Outgoing WhatsApp outbox row. API callers persist a row as QUEUED and the
 * scheduled dispatcher claims it with FOR UPDATE SKIP LOCKED. Meta status
 * callbacks advance the row monotonically and never downgrade a confirmed
 * delivery or read receipt.
 */
@Entity
@Table(name = "whatsapp_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    /** Public, non-guessable reference. Also sent as biz_opaque_callback_data. */
    @Column(name = "reference", nullable = false, unique = true, length = 36)
    private String reference;

    @Column(name = "batch_id", length = 36)
    private String batchId;

    @Column(name = "recipient", nullable = false, length = 20)
    private String recipient;

    /** Meta's canonical WhatsApp ID, usually the number without a leading '+'. */
    @Column(name = "recipient_wa_id", length = 20)
    private String recipientWaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 20)
    @Builder.Default
    private WhatsAppMessageType messageType = WhatsAppMessageType.TEXT;

    @Column(name = "template_name", length = 512)
    private String templateName;

    @Column(name = "template_language", length = 20)
    private String templateLanguage;

    /** JSON array of ordered body parameter strings. */
    @Column(name = "template_parameters", columnDefinition = "TEXT")
    private String templateParameters;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "media_url", columnDefinition = "TEXT")
    private String mediaUrl;

    @Column(name = "media_id", length = 128)
    private String mediaId;

    @Column(name = "caption", columnDefinition = "TEXT")
    private String caption;

    @Column(name = "context_message_id", length = 128)
    private String contextMessageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private WhatsAppStatus status = WhatsAppStatus.QUEUED;

    @Column(name = "provider_message_id", length = 128)
    private String providerMessageId;

    @Column(name = "provider_status", length = 40)
    private String providerStatus;

    @Column(name = "error_code")
    private Integer errorCode;

    @Column(name = "error_subcode")
    private Integer errorSubcode;

    @Column(name = "error_title", length = 255)
    private String errorTitle;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "fbtrace_id", length = 128)
    private String fbtraceId;

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

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "failed_at")
    private LocalDateTime failedAt;

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

    public enum WhatsAppStatus {
        QUEUED,
        SENDING,
        SENT,
        DELIVERED,
        READ,
        FAILED
    }

    public enum WhatsAppMessageType {
        TEXT,
        TEMPLATE,
        IMAGE,
        DOCUMENT,
        AUDIO,
        VIDEO,
        STICKER,
        LOCATION,
        INTERACTIVE
    }
}
