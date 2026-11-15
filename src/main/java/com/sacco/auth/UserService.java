package com.sacco.auth;

import com.sacco.audit.AuditService;
import com.sacco.auth.dto.CreateUserRequest;
import com.sacco.auth.dto.UserResponse;
import com.sacco.auth.entity.User;
import com.sacco.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<UserResponse> getUsers(Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.USER_MANAGE);
        return userRepository.findAll().stream().map(UserResponse::fromEntity).toList();
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.USER_MANAGE);
        if (userRepository.existsByUsername(request.getUsername().trim())) {
            throw new IllegalStateException("Username already exists");
        }
        String role = normalizeRole(request.getRole());

        User user = User.builder()
                .username(request.getUsername().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .memberId(request.getMemberId())
                .role(role)
                .isActive(true)
                .build();
        User saved = userRepository.save(user);
        auditService.logEvent(actorId, "USER_CREATED", "USER", saved.getUserId(), "Role: " + role);
        return UserResponse.fromEntity(saved);
    }

    @Transactional
    public UserResponse updateRole(Long userId, String role, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.USER_MANAGE);
        User user = findUser(userId);
        String normalized = normalizeRole(role);
        String previous = user.getRole();
        user.setRole(normalized);
        User saved = userRepository.save(user);
        auditService.logEvent(actorId, "USER_ROLE_CHANGED", "USER", userId, previous + " -> " + normalized);
        return UserResponse.fromEntity(saved);
    }

    @Transactional
    public UserResponse setActive(Long userId, boolean active, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.USER_MANAGE);
        User user = findUser(userId);
        if (user.getUserId().equals(actorId) && !active) {
            throw new IllegalStateException("You cannot deactivate your own account");
        }
        user.setIsActive(active);
        User saved = userRepository.save(user);
        auditService.logEvent(actorId, active ? "USER_ACTIVATED" : "USER_DEACTIVATED", "USER", userId, null);
        return UserResponse.fromEntity(saved);
    }

    @Transactional
    public UserResponse resetPassword(Long userId, String password, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.USER_MANAGE);
        if (password == null || password.length() < 6) {
            throw new IllegalStateException("Password must be at least 6 characters");
        }
        User user = findUser(userId);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPasswordResetRequired(false);
        User saved = userRepository.save(user);
        auditService.logEvent(actorId, "USER_PASSWORD_RESET", "USER", userId, null);
        return UserResponse.fromEntity(saved);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (!PermissionService.ROLES.contains(normalized)) {
            throw new IllegalStateException("Invalid role. Allowed: " + String.join(", ", PermissionService.ROLES));
        }
        return normalized;
    }
}
