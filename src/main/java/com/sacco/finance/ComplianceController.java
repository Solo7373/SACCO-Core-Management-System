package com.sacco.finance;

import com.sacco.audit.dto.AuditEventResponse;
import com.sacco.finance.dto.TaxBreakdownResponse;
import com.sacco.finance.dto.TransactionSheetResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/compliance")
@RequiredArgsConstructor
public class ComplianceController {

    private final ComplianceService complianceService;
    private final ComplianceExportService complianceExportService;

    @GetMapping("/transactions")
    public ResponseEntity<TransactionSheetResponse> getTransactionSheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String channel,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(complianceService.getTransactionSheet(
                fromDate, toDate, transactionType, channel, actorId));
    }

    /**
     * Sealed auditor export. {@code format} accepts csv (raw spreadsheet),
     * xlsx (workbook) or pdf (sealed PDF with SHA-256 document seal).
     */
    @GetMapping("/transactions/export")
    public ResponseEntity<byte[]> exportTransactionSheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String transactionType,
            @RequestParam(required = false) String channel,
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return download(complianceExportService.exportTransactions(
                fromDate, toDate, transactionType, channel, format, actorId));
    }

    @GetMapping("/tax-breakdown")
    public ResponseEntity<TaxBreakdownResponse> getTaxBreakdown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(complianceService.getTaxBreakdown(fromDate, toDate, actorId));
    }

    @GetMapping("/tax-breakdown/export")
    public ResponseEntity<byte[]> exportTaxBreakdown(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return download(complianceExportService.exportTaxBreakdown(fromDate, toDate, format, actorId));
    }

    @GetMapping("/audit-log")
    public ResponseEntity<List<AuditEventResponse>> getAuditLog(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(complianceService.getAuditLog(limit, action, entityType, actorId));
    }

    @GetMapping("/audit-log/export")
    public ResponseEntity<byte[]> exportAuditLog(
            @RequestParam(defaultValue = "500") int limit,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return download(complianceExportService.exportAuditLog(limit, action, entityType, format, actorId));
    }

    private ResponseEntity<byte[]> download(ComplianceExportService.ExportFile file) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + file.fileName() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType()))
                .body(file.content());
    }
}
