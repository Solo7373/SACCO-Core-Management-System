package com.sacco.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

/**
 * Administrative edit of a member's personal and contact details. Null fields
 * are left unchanged, which lets the UI send only the section being edited.
 */
@Data
public class MemberUpdateRequest {
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;

    @Pattern(regexp = "^[+]?[0-9\\s-]{7,15}$", message = "Invalid phone number")
    private String phone;

    @Email(message = "Invalid email address")
    private String email;

    private String address;
    private String gender;
    private String maritalStatus;
    private String nextOfKinName;
    private String nextOfKinRelationship;
    private String nextOfKinPhone;
    private String nextOfKinAddress;
}
