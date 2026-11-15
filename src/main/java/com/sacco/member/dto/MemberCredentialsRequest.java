package com.sacco.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Creates or updates the login credentials attached to a member profile.
 * The password is optional on update so an administrator can change the
 * username or reactivate the account without resetting the password.
 */
@Data
public class MemberCredentialsRequest {
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
    private String username;

    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;

    @Email(message = "Invalid email address")
    private String email;

    private Boolean isActive = true;
}
