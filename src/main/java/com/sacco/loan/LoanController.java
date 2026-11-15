package com.sacco.loan;

import com.sacco.auth.PermissionService;
import com.sacco.loan.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;
    private final LoanCollectionService collectionService;
    private final PermissionService permissionService;

    @PostMapping("/products")
    public ResponseEntity<LoanProductResponse> createProduct(
            @Valid @RequestBody LoanProductRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.status(HttpStatus.CREATED).body(loanService.createProduct(request, actorId));
    }

    @GetMapping("/products")
    public ResponseEntity<List<LoanProductResponse>> getProducts() {
        return ResponseEntity.ok(loanService.getProducts());
    }

    @GetMapping("/products/active")
    public ResponseEntity<List<LoanProductResponse>> getActiveProducts() {
        return ResponseEntity.ok(loanService.getActiveProducts());
    }

    @PostMapping("/applications")
    public ResponseEntity<LoanResponse> apply(
            @Valid @RequestBody LoanApplicationRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.status(HttpStatus.CREATED).body(loanService.apply(request, actorId));
    }

    @GetMapping
    public ResponseEntity<List<LoanResponse>> getAllLoans(@RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(loanService.getAllLoans(actorId));
    }

    @GetMapping("/{loanId}")
    public ResponseEntity<LoanResponse> getLoan(
            @PathVariable Long loanId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(loanService.getLoan(loanId, actorId));
    }

    @GetMapping("/member/{memberId}")
    public ResponseEntity<List<LoanResponse>> getLoansByMember(
            @PathVariable Long memberId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.requireSelfOrPrivileged(actorId, memberId);
        return ResponseEntity.ok(loanService.getLoansByMember(memberId, actorId));
    }

    @GetMapping("/arrears")
    public ResponseEntity<List<LoanResponse>> getArrears(@RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(loanService.getArrears(actorId));
    }

    @GetMapping("/eligibility")
    public ResponseEntity<LoanEligibilityResponse> checkEligibility(
            @RequestParam Long memberId,
            @RequestParam Long productId,
            @RequestParam(required = false) BigDecimal amount,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.requireSelfOrPrivileged(actorId, memberId);
        return ResponseEntity.ok(loanService.checkEligibility(memberId, productId, amount));
    }

    @PostMapping("/{loanId}/review")
    public ResponseEntity<LoanResponse> review(
            @PathVariable Long loanId,
            @RequestBody(required = false) LoanReviewRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(loanService.review(loanId, request, actorId));
    }

    @PostMapping("/{loanId}/approve")
    public ResponseEntity<LoanResponse> approve(
            @PathVariable Long loanId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(loanService.approve(loanId, actorId));
    }

    @PostMapping("/{loanId}/reject")
    public ResponseEntity<LoanResponse> reject(
            @PathVariable Long loanId,
            @Valid @RequestBody LoanRejectRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(loanService.reject(loanId, request, actorId));
    }

    @PostMapping("/{loanId}/disburse")
    public ResponseEntity<LoanResponse> disburse(
            @PathVariable Long loanId,
            @RequestBody(required = false) LoanDisbursementRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(loanService.disburse(loanId, request, actorId));
    }

    @GetMapping("/{loanId}/schedule")
    public ResponseEntity<List<LoanScheduleResponse>> getSchedule(
            @PathVariable Long loanId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(loanService.getSchedule(loanId, actorId));
    }

    @GetMapping("/{loanId}/repayments")
    public ResponseEntity<List<LoanRepaymentResponse>> getRepayments(
            @PathVariable Long loanId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(loanService.getRepayments(loanId, actorId));
    }

    @PostMapping("/{loanId}/repayments")
    public ResponseEntity<LoanRepaymentResponse> repay(
            @PathVariable Long loanId,
            @Valid @RequestBody LoanRepaymentRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.status(HttpStatus.CREATED).body(loanService.repay(loanId, request, actorId));
    }

    /**
     * Member-safe demand history for a single loan. A MEMBER actor may only
     * read demands for their own member id; staff need LOAN_VIEW.
     */
    @GetMapping("/{loanId}/demands")
    public ResponseEntity<List<LoanDemandResponse>> getDemands(
            @PathVariable Long loanId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(collectionService.getDemandsForLoan(loanId, actorId));
    }

    @GetMapping("/{loanId}/demands/{demandId}/pdf")
    public ResponseEntity<byte[]> getDemandPdf(
            @PathVariable Long loanId,
            @PathVariable Long demandId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        DemandPdf pdf = collectionService.getDemandPdfForLoan(loanId, demandId, actorId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + pdf.fileName() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf.content());
    }
}
