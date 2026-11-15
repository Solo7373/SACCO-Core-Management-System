package com.sacco.share;

import com.sacco.share.dto.*;
import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shares")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;
    private final PermissionService permissionService;

    @PostMapping("/products")
    public ResponseEntity<ShareProductResponse> createShareProduct(
            @Valid @RequestBody ShareProductRequest request, 
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.SHARE_CONFIG);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shareService.createShareProduct(request, actorId));
    }

    @PostMapping("/transactions")
    public ResponseEntity<ShareTransactionResponse> recordShareTransaction(
            @Valid @RequestBody ShareTransactionRequest request, 
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.SHARE_RECORD);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(shareService.recordShareTransaction(request, actorId));
    }

    @PostMapping("/transactions/{transactionId}/approve")
    public ResponseEntity<ShareTransactionResponse> approveShareTransaction(
            @PathVariable Long transactionId, 
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.SHARE_APPROVE);
        return ResponseEntity.ok(shareService.approveShareTransaction(transactionId, actorId));
    }

    @PostMapping("/transactions/{transactionId}/reject")
    public ResponseEntity<ShareTransactionResponse> rejectShareTransaction(
            @PathVariable Long transactionId, 
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.SHARE_APPROVE);
        return ResponseEntity.ok(shareService.rejectShareTransaction(transactionId, actorId));
    }

    @GetMapping("/products/active")
    public ResponseEntity<List<ShareProductResponse>> getActiveShareProducts() {
        return ResponseEntity.ok(shareService.getActiveShareProducts());
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<ShareTransactionResponse>> getAllShareTransactions() {
        return ResponseEntity.ok(shareService.getAllShareTransactions());
    }

    @GetMapping("/transactions/member/{memberId}")
    public ResponseEntity<List<ShareTransactionResponse>> getMemberShareTransactions(
            @PathVariable Long memberId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.requireSelfOrPrivileged(actorId, memberId);
        return ResponseEntity.ok(shareService.getMemberShareTransactions(memberId));
    }

    @GetMapping("/transactions/{transactionId}")
    public ResponseEntity<ShareTransactionResponse> getShareTransaction(@PathVariable Long transactionId) {
        return ResponseEntity.ok(shareService.getShareTransaction(transactionId));
    }

    @GetMapping("/members/{memberId}/summary")
    public ResponseEntity<ShareSummaryResponse> getMemberSummary(
            @PathVariable Long memberId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.requireSelfOrPrivileged(actorId, memberId);
        return ResponseEntity.ok(shareService.getMemberSummary(memberId));
    }

    @GetMapping("/summary")
    public ResponseEntity<SharePortfolioSummaryResponse> getPortfolioSummary() {
        return ResponseEntity.ok(shareService.getPortfolioSummary());
    }

    @GetMapping("/dividends")
    public ResponseEntity<DividendCalculationResponse> calculateDividends(
            @RequestParam(defaultValue = "15") java.math.BigDecimal rate) {
        return ResponseEntity.ok(shareService.calculateDividends(rate));
    }
}