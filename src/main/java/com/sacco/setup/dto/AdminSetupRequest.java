package com.sacco.setup.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Creates the first administrator on a new installation, or rotates the
 * existing administrator credentials during a guided upgrade. The current
 * password is required when the existing account no longer uses the factory
 * default.
 */
@Data
public class AdminSetupRequest {

    @NotBlank(message = "Administrator username is required")
    @Size(max = 50)
    private String username;

    @NotBlank(message = "Administrator email is required")
    @Email(message = "Administrator email must be valid")
    @Size(max = 200)
    private String email;

    /** Required when an existing administrator account is present. */
    private String currentPassword;

    @NotBlank(message = "Administrator password is required")
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    private String newPassword;

    @NotBlank(message = "Please confirm the administrator password")
    private String confirmPassword;
}
