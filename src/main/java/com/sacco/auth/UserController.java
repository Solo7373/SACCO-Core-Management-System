package com.sacco.auth;

import com.sacco.auth.dto.CreateUserRequest;
import com.sacco.auth.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<List<UserResponse>> getUsers(@RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(userService.getUsers(actorId));
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(request, actorId));
    }

    @PutMapping("/{userId}/role")
    public ResponseEntity<UserResponse> updateRole(
            @PathVariable Long userId,
            @RequestParam String role,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(userService.updateRole(userId, role, actorId));
    }

    @PostMapping("/{userId}/activate")
    public ResponseEntity<UserResponse> activate(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(userService.setActive(userId, true, actorId));
    }

    @PostMapping("/{userId}/deactivate")
    public ResponseEntity<UserResponse> deactivate(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(userService.setActive(userId, false, actorId));
    }

    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<UserResponse> resetPassword(
            @PathVariable Long userId,
            @RequestParam String password,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(userService.resetPassword(userId, password, actorId));
    }

    @GetMapping("/roles")
    public ResponseEntity<Map<String, Object>> getRoles() {
        return ResponseEntity.ok(Map.of(
                "roles", PermissionService.ROLES,
                "permissions", permissionService.allRolePermissions()
        ));
    }
}
