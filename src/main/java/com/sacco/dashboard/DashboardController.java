package com.sacco.dashboard;

import com.sacco.dashboard.dto.DashboardSummaryResponse;
import com.sacco.dashboard.dto.MonthlySavingsPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String granularity) {
        return ResponseEntity.ok(dashboardService.getSummary(fromDate, toDate, granularity));
    }

    @GetMapping("/savings/monthly")
    public ResponseEntity<List<MonthlySavingsPoint>> getMonthlySavings(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String granularity,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(dashboardService.getSummaryMonthlySavings(fromDate, toDate, granularity));
    }
}
