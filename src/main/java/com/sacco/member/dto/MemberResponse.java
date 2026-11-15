package com.sacco.member.dto;

import com.sacco.member.entity.Member;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class MemberResponse {
    private Long memberId;
    private String membershipNo;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String phone;
    private String email;
    private String address;
    private String gender;
    private String maritalStatus;
    private String nextOfKinName;
    private String nextOfKinRelationship;
    private String nextOfKinPhone;
    private String nextOfKinAddress;
    private String passportPhotoPath;
    private Member.MemberStatus status;
    private String rejectionReason;
    private Boolean isDeleted;
    private LocalDateTime deletedAt;
    private String deletionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static MemberResponse fromEntity(Member member) {
        return MemberResponse.builder()
                .memberId(member.getMemberId())
                .membershipNo(member.getMembershipNo())
                .firstName(member.getFirstName())
                .lastName(member.getLastName())
                .dateOfBirth(member.getDateOfBirth())
                .phone(member.getPhone())
                .email(member.getEmail())
                .address(member.getAddress())
                .gender(member.getGender())
                .maritalStatus(member.getMaritalStatus())
                .nextOfKinName(member.getNextOfKinName())
                .nextOfKinRelationship(member.getNextOfKinRelationship())
                .nextOfKinPhone(member.getNextOfKinPhone())
                .nextOfKinAddress(member.getNextOfKinAddress())
                .passportPhotoPath(member.getPassportPhotoPath())
                .status(member.getStatus())
                .rejectionReason(member.getRejectionReason())
                .isDeleted(member.getIsDeleted())
                .deletedAt(member.getDeletedAt())
                .deletionReason(member.getDeletionReason())
                .createdAt(member.getCreatedAt())
                .updatedAt(member.getUpdatedAt())
                .build();
    }
}
