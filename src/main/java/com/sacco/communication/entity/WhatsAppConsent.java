package com.sacco.communication.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Per-number WhatsApp consent and conversation state. Business-initiated
 * messages require an opt-in, and free-form replies are only allowed for
 * {@code customerServiceWindowHours} after the member's last inbound message.
 */
@Entity
@Table(name = "whatsapp_consents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WhatsAppConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consent_id")
    private Long consentId;

    @Column(name = "phone_number", nullable = false, unique = true, length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ConsentStatus status = ConsentStatus.OPT_IN;

    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "last_inbound_at")
    private LocalDateTime lastInboundAt;

    @Column(name = "last_outbound_at")
    private LocalDateTime lastOutboundAt;

    @Column(name = "opted_in_at")
    private LocalDateTime optedInAt;

    @Column(name = "opted_out_at")
    private LocalDateTime optedOutAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isOptedIn() {
        return status == ConsentStatus.OPT_IN;
    }

    public enum ConsentStatus {
        OPT_IN,
        OPT_OUT
    }
}
