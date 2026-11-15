package com.sacco.ledger;

import com.sacco.ledger.dto.*;
import com.sacco.ledger.entity.ChartOfAccounts;
import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;
    private final PermissionService permissionService;

    @PostMapping("/entries")
    public ResponseEntity<JournalEntryResponse> createAndPostEntry(
            @Valid @RequestBody JournalEntryRequest request, 
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.LEDGER_POST);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ledgerService.createAndPostEntry(request, actorId));
    }

    @PostMapping("/entries/{journalId}/reverse")
    public ResponseEntity<JournalEntryResponse> reverseEntry(
            @PathVariable Long journalId, 
            @RequestParam String reason, 
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.LEDGER_POST);
        return ResponseEntity.ok(ledgerService.reverseEntry(journalId, reason, actorId));
    }

    @GetMapping("/entries/{journalId}")
    public ResponseEntity<JournalEntryResponse> getEntry(@PathVariable Long journalId) {
        return ResponseEntity.ok(ledgerService.getEntry(journalId));
    }

    @GetMapping("/entries")
    public ResponseEntity<List<JournalEntryResponse>> getEntriesBySource(
            @RequestParam String sourceType, 
            @RequestParam Long sourceId) {
        return ResponseEntity.ok(ledgerService.getEntriesBySource(sourceType, sourceId));
    }

    @GetMapping("/accounts/{accountCode}/balance")
    public ResponseEntity<BigDecimal> getAccountBalance(@PathVariable String accountCode) {
        return ResponseEntity.ok(ledgerService.getAccountBalance(accountCode));
    }

    @GetMapping("/trial-balance")
    public ResponseEntity<List<TrialBalanceResponse>> generateTrialBalance(
            @RequestParam(required = false) 
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate) {
        LocalDate date = asOfDate != null ? asOfDate : LocalDate.now();
        return ResponseEntity.ok(ledgerService.generateTrialBalance(date));
    }

    @GetMapping("/chart-of-accounts")
    public ResponseEntity<List<ChartOfAccounts>> getChartOfAccounts() {
        return ResponseEntity.ok(ledgerService.getChartOfAccounts());
    }
}