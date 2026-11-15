package com.sacco.savings;

import com.sacco.savings.dto.SavingsTransactionRequest;
import com.sacco.savings.dto.SavingsTransactionResponse;
import com.sacco.savings.dto.SavingsTransferRequest;
import com.sacco.savings.dto.SavingsTransferResponse;
import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/savings")
@RequiredArgsConstructor
public class SavingsController {

    private final SavingsService savingsService;
    private final PermissionService permissionService;

    @GetMapping("/transactions")
    public ResponseEntity<List<SavingsTransactionResponse>> getAllTransactions() {
        return ResponseEntity.ok(savingsService.getAllTransactions());
    }

    @PostMapping("/transactions")
    public ResponseEntity<SavingsTransactionResponse> postTransaction(
            @Valid @RequestBody SavingsTransactionRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.SAVINGS_POST);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savingsService.postTransaction(request, actorId));
    }

    @PostMapping("/transfers")
    public ResponseEntity<SavingsTransferResponse> postTransfer(
            @Valid @RequestBody SavingsTransferRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.SAVINGS_POST);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savingsService.postTransfer(request, actorId));
    }

    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<SavingsTransactionResponse> getTransaction(@PathVariable Long transactionId) {
        return ResponseEntity.ok(savingsService.getTransaction(transactionId));
    }

    @PostMapping("/transactions/{transactionId}/reverse")
    public ResponseEntity<SavingsTransactionResponse> reverseTransaction(
            @PathVariable Long transactionId,
            @RequestParam String reason,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.SAVINGS_REVERSE);
        return ResponseEntity.ok(savingsService.reverseTransaction(transactionId, reason, actorId));
    }

    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<BigDecimal> getBalance(@PathVariable Long accountId) {
        return ResponseEntity.ok(savingsService.calculateBalance(accountId));
    }

    @GetMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<List<SavingsTransactionResponse>> getAccountTransactions(@PathVariable Long accountId) {
        return ResponseEntity.ok(savingsService.getAccountTransactions(accountId));
    }

    @GetMapping("/transactions/pending")
    public ResponseEntity<List<SavingsTransactionResponse>> getPendingTransactions() {
        return ResponseEntity.ok(savingsService.getPendingTransactions());
    }

    @GetMapping("/withdrawal-threshold")
    public ResponseEntity<BigDecimal> getWithdrawalThreshold() {
        return ResponseEntity.ok(savingsService.getWithdrawalApprovalThreshold());
    }
}
