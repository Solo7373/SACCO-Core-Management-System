package com.sacco.finance;

import com.sacco.audit.AuditService;
import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import com.sacco.finance.dto.FeeConfigRequest;
import com.sacco.finance.dto.FeeConfigResponse;
import com.sacco.finance.dto.TaxRateRequest;
import com.sacco.finance.dto.TaxRateResponse;
import com.sacco.finance.entity.FeeConfig;
import com.sacco.finance.entity.TaxRate;
import com.sacco.finance.repository.FeeConfigRepository;
import com.sacco.finance.repository.TaxRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class FinanceConfigService {

    private final TaxRateRepository taxRateRepository;
    private final FeeConfigRepository feeConfigRepository;
    private final PermissionService permissionService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<TaxRateResponse> getTaxRates(Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.TAX_VIEW);
        return taxRateRepository.findAllByOrderByCategoryAscNameAsc().stream()
                .map(TaxRateResponse::fromEntity)
                .toList();
    }

    @Transactional
    public TaxRateResponse createTaxRate(TaxRateRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.TAX_MANAGE);
        String code = normalizeCode(request.getCode());
        if (taxRateRepository.existsByCode(code)) {
            throw new IllegalStateException("A tax rate with code " + code + " already exists");
        }
        TaxRate rate = TaxRate.builder()
                .name(request.getName().trim())
                .code(code)
                .category(normalize(request.getCategory(), "GLOBAL"))
                .ratePercent(request.getRatePercent())
                .appliesToFees(request.getAppliesToFees() == null ? true : request.getAppliesToFees())
                .isActive(request.getIsActive() == null ? true : request.getIsActive())
                .description(request.getDescription())
                .effectiveFrom(request.getEffectiveFrom() == null ? LocalDate.now() : request.getEffectiveFrom())
                .build();
        TaxRate saved = taxRateRepository.save(rate);
        auditService.logEvent(actorId, "TAX_RATE_CREATED", "TAX_RATE", saved.getTaxRateId(),
                saved.getCode() + " @ " + saved.getRatePercent() + "%");
        return TaxRateResponse.fromEntity(saved);
    }

    @Transactional
    public TaxRateResponse updateTaxRate(Long taxRateId, TaxRateRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.TAX_MANAGE);
        TaxRate rate = findTaxRate(taxRateId);
        String code = normalizeCode(request.getCode());
        if (!code.equals(rate.getCode()) && taxRateRepository.existsByCode(code)) {
            throw new IllegalStateException("A tax rate with code " + code + " already exists");
        }
        rate.setName(request.getName().trim());
        rate.setCode(code);
        rate.setCategory(normalize(request.getCategory(), "GLOBAL"));
        rate.setRatePercent(request.getRatePercent());
        rate.setAppliesToFees(request.getAppliesToFees() == null ? rate.getAppliesToFees() : request.getAppliesToFees());
        rate.setIsActive(request.getIsActive() == null ? rate.getIsActive() : request.getIsActive());
        rate.setDescription(request.getDescription());
        if (request.getEffectiveFrom() != null) {
            rate.setEffectiveFrom(request.getEffectiveFrom());
        }
        TaxRate saved = taxRateRepository.save(rate);
        auditService.logEvent(actorId, "TAX_RATE_UPDATED", "TAX_RATE", taxRateId,
                saved.getCode() + " @ " + saved.getRatePercent() + "%");
        return TaxRateResponse.fromEntity(saved);
    }

    @Transactional
    public TaxRateResponse toggleTaxRate(Long taxRateId, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.TAX_MANAGE);
        TaxRate rate = findTaxRate(taxRateId);
        rate.setIsActive(!Boolean.TRUE.equals(rate.getIsActive()));
        TaxRate saved = taxRateRepository.save(rate);
        auditService.logEvent(actorId, "TAX_RATE_TOGGLED", "TAX_RATE", taxRateId,
                saved.getCode() + " active=" + saved.getIsActive());
        return TaxRateResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<FeeConfigResponse> getFeeConfigs(Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.FEE_VIEW);
        return feeConfigRepository.findAllByOrderByTransactionTypeAscChannelAsc().stream()
                .map(this::toFeeResponse)
                .toList();
    }

    @Transactional
    public FeeConfigResponse createFeeConfig(FeeConfigRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.FEE_MANAGE);
        FeeConfig config = FeeConfig.builder()
                .name(request.getName().trim())
                .transactionType(normalize(request.getTransactionType(), "DEPOSIT"))
                .channel(normalize(request.getChannel(), "CASH"))
                .feeType(request.getFeeType())
                .feeValue(request.getFeeValue())
                .minAmount(request.getMinAmount())
                .maxFee(request.getMaxFee())
                .taxRateId(resolveTaxRateId(request.getTaxRateId()))
                .isActive(request.getIsActive() == null ? true : request.getIsActive())
                .description(request.getDescription())
                .build();
        FeeConfig saved = feeConfigRepository.save(config);
        auditService.logEvent(actorId, "FEE_CONFIG_CREATED", "FEE_CONFIG", saved.getFeeConfigId(),
                saved.getTransactionType() + "/" + saved.getChannel() + " " + saved.getFeeType());
        return toFeeResponse(saved);
    }

    @Transactional
    public FeeConfigResponse updateFeeConfig(Long feeConfigId, FeeConfigRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.FEE_MANAGE);
        FeeConfig config = feeConfigRepository.findById(feeConfigId)
                .orElseThrow(() -> new IllegalArgumentException("Fee configuration not found: " + feeConfigId));
        config.setName(request.getName().trim());
        config.setTransactionType(normalize(request.getTransactionType(), config.getTransactionType()));
        config.setChannel(normalize(request.getChannel(), config.getChannel()));
        config.setFeeType(request.getFeeType());
        config.setFeeValue(request.getFeeValue());
        config.setMinAmount(request.getMinAmount());
        config.setMaxFee(request.getMaxFee());
        config.setTaxRateId(resolveTaxRateId(request.getTaxRateId()));
        config.setIsActive(request.getIsActive() == null ? config.getIsActive() : request.getIsActive());
        config.setDescription(request.getDescription());
        FeeConfig saved = feeConfigRepository.save(config);
        auditService.logEvent(actorId, "FEE_CONFIG_UPDATED", "FEE_CONFIG", feeConfigId,
                saved.getName() + " " + saved.getFeeType() + " " + saved.getFeeValue());
        return toFeeResponse(saved);
    }

    @Transactional
    public FeeConfigResponse toggleFeeConfig(Long feeConfigId, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.FEE_MANAGE);
        FeeConfig config = feeConfigRepository.findById(feeConfigId)
                .orElseThrow(() -> new IllegalArgumentException("Fee configuration not found: " + feeConfigId));
        config.setIsActive(!Boolean.TRUE.equals(config.getIsActive()));
        FeeConfig saved = feeConfigRepository.save(config);
        auditService.logEvent(actorId, "FEE_CONFIG_TOGGLED", "FEE_CONFIG", feeConfigId,
                saved.getName() + " active=" + saved.getIsActive());
        return toFeeResponse(saved);
    }

    private FeeConfigResponse toFeeResponse(FeeConfig config) {
        FeeConfigResponse response = FeeConfigResponse.fromEntity(config);
        if (config.getTaxRateId() != null) {
            taxRateRepository.findById(config.getTaxRateId()).ifPresent(rate -> {
                response.setTaxRateName(rate.getName());
                response.setTaxRatePercent(rate.getRatePercent());
            });
        }
        return response;
    }

    private Long resolveTaxRateId(Long taxRateId) {
        if (taxRateId == null) {
            return null;
        }
        return taxRateRepository.findById(taxRateId)
                .map(TaxRate::getTaxRateId)
                .orElseThrow(() -> new IllegalArgumentException("Tax rate not found: " + taxRateId));
    }

    private TaxRate findTaxRate(Long taxRateId) {
        return taxRateRepository.findById(taxRateId)
                .orElseThrow(() -> new IllegalArgumentException("Tax rate not found: " + taxRateId));
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeCode(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Tax code is required");
        }
        return value.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }
}
