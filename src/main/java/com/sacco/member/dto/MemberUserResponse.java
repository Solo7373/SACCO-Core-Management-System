package com.sacco.member.dto;

import com.sacco.auth.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class MemberUserResponse {
    private Long userId;
    private Long memberId;
    private String username;
    private String email;
    private String role;
    private Boolean isActive;
    private Boolean passwordResetRequired;
    private LocalDateTime lastLogin;
    private LocalDateTime createdAt;

    public static MemberUserResponse fromEntity(User user) {
        return MemberUserResponse.builder()
                .userId(user.getUserId())
                .memberId(user.getMemberId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .isActive(user.getIsActive())
                .passwordResetRequired(user.getPasswordResetRequired())
                .lastLogin(user.getLastLogin())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
