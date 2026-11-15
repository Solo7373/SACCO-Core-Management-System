package com.sacco.member.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "verification_records")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VerificationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Long verificationId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private VerificationMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private IdentityDocument.VerificationStatus outcome;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "evidence_reference", length = 500)
    private String evidenceReference;

    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    public enum VerificationMethod {
        MANUAL_REVIEW, PROVIDER_API, PHYSICAL_DOCUMENT
    }
}
