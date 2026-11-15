package com.sacco.communication.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * One OTP challenge. Only the HMAC-SHA256 hash of the code is stored; the
 * plaintext code exists only in the SMS body and in memory during the request.
 */
@Entity
@Table(name = "phone_verifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhoneVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Long verificationId;

    @Column(name = "reference", nullable = false, unique = true, length = 36)
    private String reference;

    @Column(name = "phone_number", nullable = false, length = 20)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 40)
    private OtpPurpose purpose;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private int maxAttempts = 5;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(name = "requested_by")
    private Long requestedBy;

    @Column(name = "request_ip", length = 45)
    private String requestIp;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum OtpPurpose {
        REGISTRATION,
        LOGIN,
        PASSWORD_RESET,
        PHONE_CHANGE,
        TRANSACTION
    }
}
