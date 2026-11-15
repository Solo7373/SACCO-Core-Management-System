package com.sacco.communication.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Raw delivery-report callback from Africa's Talking, retained for audit. */
@Entity
@Table(name = "sms_delivery_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SmsDeliveryReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "provider_message_id", length = 100)
    private String providerMessageId;

    @Column(name = "status", length = 40)
    private String status;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "network_code", length = 20)
    private String networkCode;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    /** False when the report arrived before the outbox row had its provider id. */
    @Column(name = "processed", nullable = false)
    @Builder.Default
    private boolean processed = false;

    @Column(name = "received_at", nullable = false)
    @Builder.Default
    private LocalDateTime receivedAt = LocalDateTime.now();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
