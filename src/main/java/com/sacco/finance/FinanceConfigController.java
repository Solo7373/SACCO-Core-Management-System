package com.sacco.finance;

import com.sacco.finance.dto.FeeConfigRequest;
import com.sacco.finance.dto.FeeConfigResponse;
import com.sacco.finance.dto.TaxRateRequest;
import com.sacco.finance.dto.TaxRateResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceConfigController {

    private final FinanceConfigService financeConfigService;

    @GetMapping("/tax-rates")
    public ResponseEntity<List<TaxRateResponse>> getTaxRates(
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(financeConfigService.getTaxRates(actorId));
    }

    @PostMapping("/tax-rates")
    public ResponseEntity<TaxRateResponse> createTaxRate(
            @Valid @RequestBody TaxRateRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(financeConfigService.createTaxRate(request, actorId));
    }

    @PutMapping("/tax-rates/{taxRateId}")
    public ResponseEntity<TaxRateResponse> updateTaxRate(
            @PathVariable Long taxRateId,
            @Valid @RequestBody TaxRateRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(financeConfigService.updateTaxRate(taxRateId, request, actorId));
    }

    @PostMapping("/tax-rates/{taxRateId}/toggle")
    public ResponseEntity<TaxRateResponse> toggleTaxRate(
            @PathVariable Long taxRateId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(financeConfigService.toggleTaxRate(taxRateId, actorId));
    }

    @GetMapping("/fee-configs")
    public ResponseEntity<List<FeeConfigResponse>> getFeeConfigs(
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(financeConfigService.getFeeConfigs(actorId));
    }

    @PostMapping("/fee-configs")
    public ResponseEntity<FeeConfigResponse> createFeeConfig(
            @Valid @RequestBody FeeConfigRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(financeConfigService.createFeeConfig(request, actorId));
    }

    @PutMapping("/fee-configs/{feeConfigId}")
    public ResponseEntity<FeeConfigResponse> updateFeeConfig(
            @PathVariable Long feeConfigId,
            @Valid @RequestBody FeeConfigRequest request,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(financeConfigService.updateFeeConfig(feeConfigId, request, actorId));
    }

    @PostMapping("/fee-configs/{feeConfigId}/toggle")
    public ResponseEntity<FeeConfigResponse> toggleFeeConfig(
            @PathVariable Long feeConfigId,
            @RequestParam(defaultValue = "1") Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        return ResponseEntity.ok(financeConfigService.toggleFeeConfig(feeConfigId, actorId));
    }
}
