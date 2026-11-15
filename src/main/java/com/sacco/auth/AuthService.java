package com.sacco.auth;

import com.sacco.audit.AuditService;
import com.sacco.auth.dto.AdminRecoveryRequest;
import com.sacco.auth.dto.ChangePasswordRequest;
import com.sacco.auth.dto.LoginRequest;
import com.sacco.auth.dto.LoginResponse;
import com.sacco.auth.dto.PasswordChangeResponse;
import com.sacco.auth.dto.RegisterUserRequest;
import com.sacco.auth.entity.User;
import com.sacco.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final AuditService auditService;
    private final JwtService jwtService;

    /**
     * Break-glass recovery key for the administrator account. Override it in
     * production through application.yml / environment variables.
     */
    @Value("${sacco.security.admin-recovery-key:}")
    private String adminRecoveryKey;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!user.getIsActive()) {
            throw new IllegalStateException("Account is deactivated");
        }

        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);

        String token = jwtService.generateToken(user);

        return LoginResponse.builder()
                .token(token)
                .userId(user.getUserId())
                .username(user.getUsername())
                .role(user.getRole())
                .memberId(user.getMemberId())
                .passwordResetRequired(Boolean.TRUE.equals(user.getPasswordResetRequired()))
                .message("Login successful")
                .build();
    }

    @Transactional
    public LoginResponse registerUser(RegisterUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalStateException("Username already exists");
        }

        User user = User.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .memberId(request.getMemberId())
                .role(request.getRole() != null ? request.getRole() : "MEMBER")
                .isActive(true)
                .build();

        User saved = userRepository.save(user);
        String token = UUID.randomUUID().toString();

        return LoginResponse.builder()
                .token(token)
                .userId(saved.getUserId())
                .username(saved.getUsername())
                .role(saved.getRole())
                .memberId(saved.getMemberId())
                .message("Registration successful")
                .build();
    }

    /**
     * Self-service password change. The caller must prove ownership by
     * supplying the current password; no administrator override here.
     */
    @Transactional
    public PasswordChangeResponse changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            auditService.logEvent(userId, "PASSWORD_CHANGE_FAILED", "USER", userId,
                    "Current password verification failed");
            throw new IllegalStateException("Current password is incorrect");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalStateException("The new password must be different from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordResetRequired(false);
        userRepository.save(user);
        auditService.logEvent(userId, "PASSWORD_CHANGED", "USER", userId, "Self-service password change");

        return PasswordChangeResponse.builder()
                .username(user.getUsername())
                .message("Password updated successfully")
                .build();
    }

    /**
     * Emergency administrator recovery using a server-side recovery key. The
     * key is never returned to the client and is compared in constant time.
     */
    @Transactional
    public PasswordChangeResponse recoverAdmin(AdminRecoveryRequest request) {
        if (adminRecoveryKey == null || adminRecoveryKey.isBlank()) {
            throw new IllegalStateException("Administrator recovery is not configured");
        }
        if (!constantTimeEquals(adminRecoveryKey, request.getRecoveryKey())) {
            auditService.logEvent(null, "ADMIN_RECOVERY_FAILED", "USER", 0L,
                    "Invalid recovery key supplied");
            throw new IllegalStateException("Invalid recovery key");
        }

        String username = request.getUsername() == null || request.getUsername().trim().isEmpty()
                ? "admin"
                : request.getUsername().trim().toLowerCase(Locale.ROOT);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new IllegalStateException("Recovery is restricted to administrator accounts");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setIsActive(true);
        user.setPasswordResetRequired(false);
        userRepository.save(user);
        auditService.logEvent(user.getUserId(), "ADMIN_ACCOUNT_RECOVERED", "USER", user.getUserId(),
                "Administrator password recovered with the configured recovery key");

        return PasswordChangeResponse.builder()
                .username(user.getUsername())
                .message("Administrator password has been reset")
                .build();
    }

    private boolean constantTimeEquals(String expected, String supplied) {
        if (expected == null || supplied == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                supplied.getBytes(StandardCharsets.UTF_8));
    }
}
