package com.sacco.communication.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Local mirror of a Meta message template. Templates must be approved by Meta
 * before they can open a business-initiated conversation, so the outbox checks
 * this table before sending. The registry is refreshed from the WABA endpoint.
 */
@Entity
@Table(name = "whatsapp_templates",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_whatsapp_templates_name_language",
                columnNames = {"name", "language"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "meta_template_id", length = 64)
    private String metaTemplateId;

    @Column(name = "name", nullable = false, length = 512)
    private String name;

    @Column(name = "language", nullable = false, length = 20)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    @Builder.Default
    private TemplateCategory category = TemplateCategory.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private TemplateStatus status = TemplateStatus.UNKNOWN;

    /** Raw component JSON returned by Meta. */
    @Column(name = "components", columnDefinition = "TEXT")
    private String components;

    /** Number of body placeholders ({{1}}, {{2}}, ...). */
    @Column(name = "parameter_count", nullable = false)
    @Builder.Default
    private int parameterCount = 0;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isApproved() {
        return status == TemplateStatus.APPROVED;
    }

    public enum TemplateCategory {
        MARKETING,
        UTILITY,
        AUTHENTICATION,
        SERVICE,
        UNKNOWN
    }

    public enum TemplateStatus {
        APPROVED,
        PENDING,
        REJECTED,
        PAUSED,
        DISABLED,
        UNKNOWN
    }
}
