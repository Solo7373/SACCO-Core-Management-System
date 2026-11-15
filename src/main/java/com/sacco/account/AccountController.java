package com.sacco.account;

import com.sacco.account.dto.*;
import com.sacco.account.entity.Account;
import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final PermissionService permissionService;

    @GetMapping
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        return ResponseEntity.ok(accountService.getAllAccounts());
    }

    @PostMapping
    public ResponseEntity<AccountResponse> openAccount(
            @Valid @RequestBody AccountRequest request, 
            @RequestParam Long primaryMemberId, 
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.ACCOUNT_MANAGE);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.openAccount(request, primaryMemberId, actorId));
    }

    @PostMapping("/{accountId}/activate")
    public ResponseEntity<AccountResponse> activateAccount(
            @PathVariable Long accountId, 
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.ACCOUNT_EDIT);
        return ResponseEntity.ok(accountService.activateAccount(accountId, actorId));
    }

    @PostMapping("/{accountId}/freeze")
    public ResponseEntity<AccountResponse> freezeAccount(
            @PathVariable Long accountId, 
            @RequestParam String reason, 
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.ACCOUNT_EDIT);
        return ResponseEntity.ok(accountService.freezeAccount(accountId, reason, actorId));
    }

    @PostMapping("/{accountId}/unfreeze")
    public ResponseEntity<AccountResponse> unfreezeAccount(
            @PathVariable Long accountId, 
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.ACCOUNT_EDIT);
        return ResponseEntity.ok(accountService.unfreezeAccount(accountId, actorId));
    }

    @PostMapping("/{accountId}/close")
    public ResponseEntity<AccountResponse> closeAccount(
            @PathVariable Long accountId, 
            @RequestParam String reason, 
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.ACCOUNT_EDIT);
        return ResponseEntity.ok(accountService.closeAccount(accountId, reason, actorId));
    }

    @PostMapping("/{accountId}/dormant")
    public ResponseEntity<AccountResponse> markDormant(
            @PathVariable Long accountId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.ACCOUNT_EDIT);
        return ResponseEntity.ok(accountService.markDormant(accountId, actorId));
    }

    @PostMapping("/run-dormancy-check")
    public ResponseEntity<Integer> runDormancyCheck(
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.ACCOUNT_EDIT);
        return ResponseEntity.ok(accountService.runDormancyCheck(actorId));
    }

    @PostMapping("/{accountId}/holders")
    public ResponseEntity<AccountHolderResponse> addAccountHolder(
            @PathVariable Long accountId, 
            @Valid @RequestBody AccountHolderRequest request, 
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.ACCOUNT_EDIT);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.addAccountHolder(accountId, request, actorId));
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable Long accountId) {
        return ResponseEntity.ok(accountService.getAccount(accountId));
    }

    @GetMapping("/{accountId}/holders")
    public ResponseEntity<List<AccountHolderResponse>> getAccountHolders(@PathVariable Long accountId) {
        return ResponseEntity.ok(accountService.getAccountHolders(accountId));
    }

    @GetMapping("/member/{memberId}")
    public ResponseEntity<List<AccountResponse>> getAccountsByMember(
            @PathVariable Long memberId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.requireSelfOrPrivileged(actorId, memberId);
        return ResponseEntity.ok(accountService.getAccountsByMember(memberId));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<AccountResponse>> getAccountsByStatus(@PathVariable String status) {
        return ResponseEntity.ok(accountService.getAccountsByStatus(Account.AccountStatus.valueOf(status)));
    }
}