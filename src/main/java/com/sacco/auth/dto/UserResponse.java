package com.sacco.auth.dto;

import com.sacco.auth.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {
    private Long userId;
    private String username;
    private String email;
    private Long memberId;
    private String role;
    private Boolean isActive;
    private Boolean passwordResetRequired;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;

    public static UserResponse fromEntity(User user) {
        return UserResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .memberId(user.getMemberId())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .passwordResetRequired(user.getPasswordResetRequired())
                .lastLogin(user.getLastLogin())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
