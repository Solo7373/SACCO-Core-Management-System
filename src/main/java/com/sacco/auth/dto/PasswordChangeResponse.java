package com.sacco.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PasswordChangeResponse {
    private String username;
    private String message;
}
