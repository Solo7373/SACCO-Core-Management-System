package com.sacco.auth;

import com.sacco.auth.dto.AdminRecoveryRequest;
import com.sacco.auth.dto.ChangePasswordRequest;
import com.sacco.auth.dto.LoginRequest;
import com.sacco.auth.dto.LoginResponse;
import com.sacco.auth.dto.PasswordChangeResponse;
import com.sacco.auth.dto.RegisterUserRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public ResponseEntity<LoginResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.registerUser(request));
    }

    @PostMapping("/change-password")
    public ResponseEntity<PasswordChangeResponse> changePassword(
            @RequestParam Long userId,
            @Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(authService.changePassword(userId, request));
    }

    @PostMapping("/recover-admin")
    public ResponseEntity<PasswordChangeResponse> recoverAdmin(
            @Valid @RequestBody AdminRecoveryRequest request) {
        return ResponseEntity.ok(authService.recoverAdmin(request));
    }
}
