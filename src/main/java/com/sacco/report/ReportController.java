package com.sacco.report;

import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import com.sacco.ledger.dto.TrialBalanceResponse;
import com.sacco.report.dto.BalanceSheetResponse;
import com.sacco.report.dto.IncomeExpenditureResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportingService reportingService;
    private final PermissionService permissionService;

    @GetMapping("/trial-balance")
    public ResponseEntity<List<TrialBalanceResponse>> getTrialBalance(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.REPORT_VIEW);
        return ResponseEntity.ok(reportingService.getTrialBalance(asOfDate));
    }

    @GetMapping("/balance-sheet")
    public ResponseEntity<BalanceSheetResponse> getBalanceSheet(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.REPORT_VIEW);
        return ResponseEntity.ok(reportingService.getBalanceSheet(asOfDate));
    }

    @GetMapping("/income-expenditure")
    public ResponseEntity<IncomeExpenditureResponse> getIncomeExpenditure(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.REPORT_VIEW);
        return ResponseEntity.ok(reportingService.getIncomeExpenditure(fromDate, toDate));
    }

    @GetMapping("/member/{memberId}/statement")
    public ResponseEntity<byte[]> getMemberStatement(
            @PathVariable Long memberId,
            @RequestParam(defaultValue = "pdf") String format,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.REPORT_VIEW);
        if ("csv".equalsIgnoreCase(format)) {
            byte[] content = reportingService.getMemberStatementCsv(memberId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=member-" + memberId + "-statement.csv")
                    .contentType(new MediaType("text", "csv"))
                    .body(content);
        }
        byte[] content = reportingService.getMemberStatementPdf(memberId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=member-" + memberId + "-statement.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(content);
    }
}
