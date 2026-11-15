package com.sacco.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Emergency recovery for the administrator account. The recovery key is
 * configured through sacco.security.admin-recovery-key and must never be
 * exposed to the browser.
 */
@Data
public class AdminRecoveryRequest {
    @NotBlank(message = "Recovery key is required")
    private String recoveryKey;

    /** Defaults to the built-in "admin" account when omitted. */
    private String username;

    @NotBlank(message = "New password is required")
    @Size(min = 8, message = "New password must be at least 8 characters")
    private String newPassword;
}
