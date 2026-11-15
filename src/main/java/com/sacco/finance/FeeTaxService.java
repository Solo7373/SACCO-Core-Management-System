package com.sacco.finance;

import com.sacco.finance.dto.ChargeCalculation;
import com.sacco.finance.entity.FeeConfig;
import com.sacco.finance.entity.TaxRate;
import com.sacco.finance.entity.TransactionCharge;
import com.sacco.finance.repository.FeeConfigRepository;
import com.sacco.finance.repository.TaxRateRepository;
import com.sacco.finance.repository.TransactionChargeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Applies configurable transaction fees and taxes. Every posted transaction is
 * written to transaction_charges so auditors get a complete breakdown sheet
 * even when the applicable fee is zero.
 */
@Service
@RequiredArgsConstructor
public class FeeTaxService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final FeeConfigRepository feeConfigRepository;
    private final TaxRateRepository taxRateRepository;
    private final TransactionChargeRepository transactionChargeRepository;

    @Transactional(readOnly = true)
    public ChargeCalculation calculate(String transactionType, String channel, BigDecimal amount) {
        BigDecimal gross = amount == null ? BigDecimal.ZERO : amount;
        String type = normalize(transactionType, "DEPOSIT");
        String ch = normalize(channel, "CASH");

        Optional<FeeConfig> configOpt =
                feeConfigRepository.findFirstByTransactionTypeAndChannelAndIsActiveTrue(type, ch);

        BigDecimal fee = BigDecimal.ZERO;
        Long feeConfigId = null;
        String feeName = null;

        if (configOpt.isPresent()) {
            FeeConfig config = configOpt.get();
            feeConfigId = config.getFeeConfigId();
            feeName = config.getName();

            boolean meetsMinimum = config.getMinAmount() == null
                    || gross.compareTo(config.getMinAmount()) >= 0;
            if (meetsMinimum) {
                if (config.getFeeType() == FeeConfig.FeeType.FIXED) {
                    fee = config.getFeeValue();
                } else {
                    fee = gross.multiply(config.getFeeValue())
                            .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
                }
                if (config.getMaxFee() != null && fee.compareTo(config.getMaxFee()) > 0) {
                    fee = config.getMaxFee();
                }
            }
            if (fee.compareTo(gross) > 0) {
                fee = gross;
            }
        }

        TaxRate taxRate = configOpt.map(config -> resolveTaxRate(config, type)).orElse(null);
        BigDecimal tax = BigDecimal.ZERO;
        Long taxRateId = null;
        BigDecimal taxRatePercent = null;
        String taxRateName = null;

        if (taxRate != null) {
            taxRateId = taxRate.getTaxRateId();
            taxRatePercent = taxRate.getRatePercent();
            taxRateName = taxRate.getName();
            tax = fee.multiply(taxRate.getRatePercent())
                    .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
        }

        if (fee.add(tax).compareTo(gross) > 0) {
            tax = gross.subtract(fee).max(BigDecimal.ZERO);
        }

        BigDecimal net = gross.subtract(fee).subtract(tax);
        return new ChargeCalculation(fee, tax, net, feeConfigId, feeName,
                taxRateId, taxRatePercent, taxRateName);
    }

    @Transactional
    public TransactionCharge record(String reference, String transactionType, String channel,
            Long memberId, Long accountId, BigDecimal grossAmount,
            ChargeCalculation calculation, String description, Long postedBy) {
        TransactionCharge charge = TransactionCharge.builder()
                .reference(reference)
                .transactionType(normalize(transactionType, "UNKNOWN"))
                .channel(normalize(channel, "CASH"))
                .memberId(memberId)
                .accountId(accountId)
                .grossAmount(grossAmount)
                .feeAmount(calculation.feeAmount())
                .taxAmount(calculation.taxAmount())
                .netAmount(calculation.netAmount())
                .feeConfigId(calculation.feeConfigId())
                .taxRateId(calculation.taxRateId())
                .taxRatePercent(calculation.taxRatePercent())
                .description(description)
                .postedBy(postedBy)
                .build();
        return transactionChargeRepository.save(charge);
    }

    private TaxRate resolveTaxRate(FeeConfig config, String transactionType) {
        if (config.getTaxRateId() != null) {
            return taxRateRepository.findById(config.getTaxRateId())
                    .filter(rate -> Boolean.TRUE.equals(rate.getIsActive()))
                    .orElse(null);
        }
        String category = switch (transactionType) {
            case "DEPOSIT", "WITHDRAWAL" -> "SAVINGS";
            case "TRANSFER" -> "TRANSFER";
            case "LOAN_REPAYMENT" -> "LOANS";
            case "SHARE_PURCHASE" -> "SHARES";
            default -> "GLOBAL";
        };
        return taxRateRepository.findFirstByCategoryAndIsActiveTrueOrderByRatePercentDesc(category)
                .filter(rate -> Boolean.TRUE.equals(rate.getAppliesToFees()))
                .or(() -> taxRateRepository.findFirstByCategoryAndIsActiveTrueOrderByRatePercentDesc("GLOBAL")
                        .filter(rate -> Boolean.TRUE.equals(rate.getAppliesToFees())))
                .orElse(null);
    }

    public String normalize(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim().toUpperCase();
    }
}
