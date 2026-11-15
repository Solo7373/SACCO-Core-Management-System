package com.sacco.loan;

import com.sacco.auth.SecurityUtils;
import com.sacco.loan.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Staff collections workspace. Every endpoint is gated by the permission
 * checks inside {@link LoanCollectionService}; member self-service demand
 * access lives on the loan controller instead.
 */
@RestController
@RequestMapping("/api/collections")
@RequiredArgsConstructor
public class LoanCollectionController {

    private final LoanCollectionService collectionService;

    @GetMapping("/summary")
    public ResponseEntity<CollectionSummary> summary(
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(collectionService.getSummary(currentActor(actorId)));
    }

    @GetMapping("/loans")
    public ResponseEntity<List<CollectionLoanRow>> loans(
            @RequestParam(required = false) String bucket,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(collectionService.getCollections(
                currentActor(actorId), bucket, search));
    }

    @GetMapping("/loans/{loanId}")
    public ResponseEntity<LoanCollectionDetailResponse> detail(
            @PathVariable Long loanId,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(collectionService.getDetail(loanId, currentActor(actorId)));
    }

    @GetMapping("/demands")
    public ResponseEntity<List<LoanDemandResponse>> demands(
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(collectionService.getAllDemands(currentActor(actorId)));
    }

    @GetMapping("/loans/{loanId}/demands")
    public ResponseEntity<List<LoanDemandResponse>> loanDemands(
            @PathVariable Long loanId,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(collectionService.getDemandsForLoan(
                loanId, currentActor(actorId)));
    }

    @PostMapping("/loans/{loanId}/demands")
    public ResponseEntity<LoanDemandResponse> issueDemand(
            @PathVariable Long loanId,
            @Valid @RequestBody LoanDemandRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(collectionService.issueDemand(loanId, request, currentActor(actorId)));
    }

    @PostMapping("/demands/{demandId}/status")
    public ResponseEntity<LoanDemandResponse> updateDemandStatus(
            @PathVariable Long demandId,
            @Valid @RequestBody DemandStatusRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(collectionService.updateDemandStatus(
                demandId, request, currentActor(actorId)));
    }

    @GetMapping("/loans/{loanId}/actions")
    public ResponseEntity<List<CollectionActionResponse>> actions(
            @PathVariable Long loanId,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(collectionService.getActions(loanId, currentActor(actorId)));
    }

    @PostMapping("/loans/{loanId}/actions")
    public ResponseEntity<CollectionActionResponse> recordAction(
            @PathVariable Long loanId,
            @Valid @RequestBody CollectionActionRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(collectionService.recordAction(loanId, request, currentActor(actorId)));
    }

    @GetMapping("/loans/{loanId}/demands/{demandId}/pdf")
    public ResponseEntity<byte[]> demandPdf(
            @PathVariable Long loanId,
            @PathVariable Long demandId,
            @RequestParam(defaultValue = "1") Long actorId) {
        DemandPdf pdf = collectionService.getDemandPdfForLoan(
                loanId, demandId, currentActor(actorId));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + pdf.fileName() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf.content());
    }

    private Long currentActor(Long fallback) {
        Long authenticated = SecurityUtils.currentUserId();
        return authenticated != null ? authenticated : fallback;
    }
}
