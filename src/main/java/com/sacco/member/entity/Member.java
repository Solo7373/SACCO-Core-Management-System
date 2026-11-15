package com.sacco.member.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "members")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "membership_no", nullable = false, unique = true, length = 32)
    private String membershipNo;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 200)
    private String email;

    @Column(name = "address")
    private String address;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "marital_status", length = 30)
    private String maritalStatus;

    @Column(name = "next_of_kin_name", length = 200)
    private String nextOfKinName;

    @Column(name = "next_of_kin_relationship", length = 100)
    private String nextOfKinRelationship;

    @Column(name = "next_of_kin_phone", length = 20)
    private String nextOfKinPhone;

    @Column(name = "next_of_kin_address", length = 500)
    private String nextOfKinAddress;

    @Column(name = "passport_photo_path", length = 500)
    private String passportPhotoPath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private MemberStatus status = MemberStatus.PENDING;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    /**
     * Soft-delete marker. Rows are never physically removed so that every
     * savings, loan, share and ledger foreign key remains valid forever.
     */
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deletion_reason", length = 500)
    private String deletionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum MemberStatus {
        PENDING, ACTIVE, SUSPENDED, REJECTED, CLOSED, INACTIVE
    }
}
