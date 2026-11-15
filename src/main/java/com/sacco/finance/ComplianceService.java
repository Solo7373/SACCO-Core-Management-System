package com.sacco.finance;

import com.sacco.audit.AuditEventRepository;
import com.sacco.audit.dto.AuditEventResponse;
import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import com.sacco.finance.dto.TaxBreakdownResponse;
import com.sacco.finance.dto.TaxBreakdownRow;
import com.sacco.finance.dto.TransactionSheetResponse;
import com.sacco.finance.dto.TransactionSheetRow;
import com.sacco.finance.entity.TransactionCharge;
import com.sacco.finance.repository.TaxRateRepository;
import com.sacco.finance.repository.TransactionChargeRepository;
import com.sacco.inventory.entity.ProductImport;
import com.sacco.inventory.repository.ProductImportRepository;
import com.sacco.loan.entity.Loan;
import com.sacco.loan.entity.LoanRepayment;
import com.sacco.loan.repository.LoanRepaymentRepository;
import com.sacco.loan.repository.LoanRepository;
import com.sacco.member.entity.Member;
import com.sacco.member.repository.MemberRepository;
import com.sacco.share.entity.ShareTransaction;
import com.sacco.share.repository.ShareTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the auditor-only transaction spreadsheet, tax breakdown and detailed
 * audit log. Read-only by design: no method in this service mutates financial
 * data.
 */
@Service
@RequiredArgsConstructor
public class ComplianceService {

    private final TransactionChargeRepository chargeRepository;
    private final TaxRateRepository taxRateRepository;
    private final LoanRepaymentRepository loanRepaymentRepository;
    private final LoanRepository loanRepository;
    private final ShareTransactionRepository shareTransactionRepository;
    private final ProductImportRepository productImportRepository;
    private final MemberRepository memberRepository;
    private final AuditEventRepository auditEventRepository;
    private final PermissionService permissionService;

    @Transactional(readOnly = true)
    public TransactionSheetResponse getTransactionSheet(LocalDate fromDate, LocalDate toDate,
            String transactionType, String channel, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.COMPLIANCE_VIEW);
        List<TransactionSheetRow> rows = buildRows(fromDate, toDate, transactionType, channel);

        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalFees = BigDecimal.ZERO;
        BigDecimal totalTaxes = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;
        for (TransactionSheetRow row : rows) {
            totalGross = totalGross.add(row.getGrossAmount());
            totalFees = totalFees.add(row.getFeeAmount());
            totalTaxes = totalTaxes.add(row.getTaxAmount());
            totalNet = totalNet.add(row.getNetAmount());
        }

        return TransactionSheetResponse.builder()
                .rows(rows)
                .count(rows.size())
                .totalGross(totalGross)
                .totalFees(totalFees)
                .totalTaxes(totalTaxes)
                .totalNet(totalNet)
                .build();
    }

    @Transactional(readOnly = true)
    public String getTransactionSheetCsv(LocalDate fromDate, LocalDate toDate,
            String transactionType, String channel, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        TransactionSheetResponse sheet = getTransactionSheet(fromDate, toDate, transactionType, channel, actorId);
        StringBuilder csv = new StringBuilder();
        csv.append("Date,Reference,Source,Transaction Type,Channel,Member,Gross Amount,Fee Amount,Tax Amount,Net Amount,Description\n");
        for (TransactionSheetRow row : sheet.getRows()) {
            csv.append(csvValue(row.getDate())).append(',')
                    .append(csvValue(row.getReference())).append(',')
                    .append(csvValue(row.getSource())).append(',')
                    .append(csvValue(row.getTransactionType())).append(',')
                    .append(csvValue(row.getChannel())).append(',')
                    .append(csvValue(row.getMemberName())).append(',')
                    .append(plain(row.getGrossAmount())).append(',')
                    .append(plain(row.getFeeAmount())).append(',')
                    .append(plain(row.getTaxAmount())).append(',')
                    .append(plain(row.getNetAmount())).append(',')
                    .append(csvValue(row.getDescription())).append('\n');
        }
        csv.append("TOTALS,,,,,,")
                .append(plain(sheet.getTotalGross())).append(',')
                .append(plain(sheet.getTotalFees())).append(',')
                .append(plain(sheet.getTotalTaxes())).append(',')
                .append(plain(sheet.getTotalNet())).append(",\n");
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public TaxBreakdownResponse getTaxBreakdown(LocalDate fromDate, LocalDate toDate, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.COMPLIANCE_VIEW);
        LocalDateTime from = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime to = toDate == null ? null : toDate.plusDays(1).atStartOfDay();
        List<TransactionCharge> charges = from == null || to == null
                ? chargeRepository.findAllByOrderByCreatedAtDesc()
                : chargeRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);

        Map<Long, String> taxNames = new HashMap<>();
        Map<Long, String> taxCodes = new HashMap<>();
        Map<Long, String> taxCategories = new HashMap<>();
        Map<Long, BigDecimal> taxRates = new HashMap<>();
        taxRateRepository.findAll().forEach(rate -> {
            taxNames.put(rate.getTaxRateId(), rate.getName());
            taxCodes.put(rate.getTaxRateId(), rate.getCode());
            taxCategories.put(rate.getTaxRateId(), rate.getCategory());
            taxRates.put(rate.getTaxRateId(), rate.getRatePercent());
        });

        Map<String, TaxAccumulator> grouped = new LinkedHashMap<>();
        for (TransactionCharge charge : charges) {
            if (charge.getTaxAmount() == null || charge.getTaxAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String key = charge.getTaxRateId() == null ? "GLOBAL" : String.valueOf(charge.getTaxRateId());
            TaxAccumulator acc = grouped.computeIfAbsent(key, k -> new TaxAccumulator(
                    charge.getTaxRateId() == null ? "Tax on fees" : taxNames.getOrDefault(charge.getTaxRateId(), "Tax on fees"),
                    charge.getTaxRateId() == null ? "GLOBAL" : taxCodes.getOrDefault(charge.getTaxRateId(), "TAX"),
                    charge.getTaxRateId() == null ? "GLOBAL" : taxCategories.getOrDefault(charge.getTaxRateId(), "GLOBAL"),
                    charge.getTaxRatePercent() != null ? charge.getTaxRatePercent()
                            : taxRates.getOrDefault(charge.getTaxRateId(), BigDecimal.ZERO)
            ));
            acc.taxable = acc.taxable.add(charge.getFeeAmount());
            acc.collected = acc.collected.add(charge.getTaxAmount());
            acc.count++;
        }

        // Import taxes are tracked on the procurement record rather than fees.
        List<ProductImport> imports = productImportRepository.findAllByOrderByCreatedAtDesc();
        BigDecimal customsTaxable = BigDecimal.ZERO;
        BigDecimal customsCollected = BigDecimal.ZERO;
        BigDecimal vatTaxable = BigDecimal.ZERO;
        BigDecimal vatCollected = BigDecimal.ZERO;
        BigDecimal otherCollected = BigDecimal.ZERO;
        long customsCount = 0;
        long vatCount = 0;
        long otherCount = 0;
        for (ProductImport item : imports) {
            if (!withinRange(item.getOrderDate() != null ? item.getOrderDate() : item.getCreatedAt().toLocalDate(),
                    fromDate, toDate)) {
                continue;
            }
            BigDecimal base = item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity()));
            if (item.getCustomsDutyAmount().compareTo(BigDecimal.ZERO) > 0) {
                customsTaxable = customsTaxable.add(base);
                customsCollected = customsCollected.add(item.getCustomsDutyAmount());
                customsCount++;
            }
            if (item.getVatAmount().compareTo(BigDecimal.ZERO) > 0) {
                vatTaxable = vatTaxable.add(base.add(item.getShippingFee()).add(item.getCustomsDutyAmount()));
                vatCollected = vatCollected.add(item.getVatAmount());
                vatCount++;
            }
            if (item.getOtherTaxes().compareTo(BigDecimal.ZERO) > 0) {
                otherCollected = otherCollected.add(item.getOtherTaxes());
                otherCount++;
            }
        }
        if (customsCount > 0) {
            grouped.put("IMPORT_CUSTOMS", new TaxAccumulator("Customs Duty", "CUSTOMS_DUTY", "IMPORT",
                    weightedRate(customsCollected, customsTaxable), customsTaxable, customsCollected, customsCount));
        }
        if (vatCount > 0) {
            grouped.put("IMPORT_VAT", new TaxAccumulator("Import VAT", "IMPORT_VAT", "IMPORT",
                    weightedRate(vatCollected, vatTaxable), vatTaxable, vatCollected, vatCount));
        }
        if (otherCount > 0) {
            grouped.put("IMPORT_OTHER", new TaxAccumulator("Other Import Taxes", "IMPORT_OTHER", "IMPORT",
                    BigDecimal.ZERO, BigDecimal.ZERO, otherCollected, otherCount));
        }

        BigDecimal totalTaxable = BigDecimal.ZERO;
        BigDecimal totalCollected = BigDecimal.ZERO;
        List<TaxBreakdownRow> rows = new ArrayList<>();
        for (TaxAccumulator acc : grouped.values()) {
            totalTaxable = totalTaxable.add(acc.taxable);
            totalCollected = totalCollected.add(acc.collected);
            rows.add(TaxBreakdownRow.builder()
                    .taxName(acc.name)
                    .taxCode(acc.code)
                    .category(acc.category)
                    .ratePercent(acc.rate)
                    .taxableAmount(acc.taxable)
                    .taxCollected(acc.collected)
                    .transactionCount(acc.count)
                    .build());
        }

        return TaxBreakdownResponse.builder()
                .rows(rows)
                .totalTaxableAmount(totalTaxable)
                .totalTaxCollected(totalCollected)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> getAuditLog(int limit, String action, String entityType, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        permissionService.require(actorId, Permission.COMPLIANCE_VIEW);
        int safeLimit = Math.min(Math.max(limit, 1), 500);
        String actionFilter = action == null ? null : action.trim().toUpperCase(Locale.ROOT);
        String entityFilter = entityType == null ? null : entityType.trim().toUpperCase(Locale.ROOT);
        return auditEventRepository.findAllByOrderByTimestampDesc().stream()
                .filter(event -> actionFilter == null || actionFilter.isEmpty()
                        || event.getAction().toUpperCase(Locale.ROOT).contains(actionFilter))
                .filter(event -> entityFilter == null || entityFilter.isEmpty()
                        || event.getEntityType().toUpperCase(Locale.ROOT).equals(entityFilter))
                .limit(safeLimit)
                .map(AuditEventResponse::fromEntity)
                .toList();
    }

    private List<TransactionSheetRow> buildRows(LocalDate fromDate, LocalDate toDate,
            String transactionType, String channel) {
        Map<Long, String> memberNames = new HashMap<>();
        for (Member member : memberRepository.findAll()) {
            memberNames.put(member.getMemberId(), member.getFirstName() + " " + member.getLastName());
        }

        LocalDateTime from = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime to = toDate == null ? null : toDate.plusDays(1).atStartOfDay();
        List<TransactionCharge> charges = from == null || to == null
                ? chargeRepository.findAllByOrderByCreatedAtDesc()
                : chargeRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(from, to);

        List<TransactionSheetRow> rows = new ArrayList<>();
        for (TransactionCharge charge : charges) {
            rows.add(TransactionSheetRow.builder()
                    .date(charge.getCreatedAt() == null ? null : charge.getCreatedAt().toLocalDate())
                    .reference(charge.getReference())
                    .source("SAVINGS")
                    .transactionType(charge.getTransactionType())
                    .channel(charge.getChannel())
                    .memberId(charge.getMemberId())
                    .memberName(charge.getMemberId() == null ? null : memberNames.get(charge.getMemberId()))
                    .grossAmount(charge.getGrossAmount())
                    .feeAmount(charge.getFeeAmount())
                    .taxAmount(charge.getTaxAmount())
                    .netAmount(charge.getNetAmount())
                    .description(charge.getDescription())
                    .build());
        }

        Map<Long, Long> loanMembers = new HashMap<>();
        for (Loan loan : loanRepository.findAll()) {
            loanMembers.put(loan.getLoanId(), loan.getMemberId());
        }
        for (LoanRepayment repayment : loanRepaymentRepository.findAll()) {
            Long memberId = loanMembers.get(repayment.getLoanId());
            rows.add(TransactionSheetRow.builder()
                    .date(repayment.getPaymentDate())
                    .reference(repayment.getReference())
                    .source("LOAN_REPAYMENT")
                    .transactionType("LOAN_REPAYMENT")
                    .channel("CASH")
                    .memberId(memberId)
                    .memberName(memberId == null ? null : memberNames.get(memberId))
                    .grossAmount(repayment.getAmount())
                    .feeAmount(BigDecimal.ZERO)
                    .taxAmount(BigDecimal.ZERO)
                    .netAmount(repayment.getAmount())
                    .description("Loan #" + repayment.getLoanId() + " repayment")
                    .build());
        }

        for (ShareTransaction tx : shareTransactionRepository.findAll()) {
            rows.add(TransactionSheetRow.builder()
                    .date(tx.getTransactionDate())
                    .reference(tx.getReference())
                    .source("SHARES")
                    .transactionType("SHARE_" + tx.getTransactionType().name())
                    .channel("CASH")
                    .memberId(tx.getMemberId())
                    .memberName(memberNames.get(tx.getMemberId()))
                    .grossAmount(tx.getAmount())
                    .feeAmount(BigDecimal.ZERO)
                    .taxAmount(BigDecimal.ZERO)
                    .netAmount(tx.getAmount())
                    .description(tx.getQuantity() + " shares @ " + tx.getUnitPrice())
                    .build());
        }

        for (ProductImport item : productImportRepository.findAllByOrderByCreatedAtDesc()) {
            BigDecimal base = item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity()));
            BigDecimal taxes = item.getCustomsDutyAmount().add(item.getVatAmount()).add(item.getOtherTaxes());
            rows.add(TransactionSheetRow.builder()
                    .date(item.getOrderDate() != null ? item.getOrderDate()
                            : item.getCreatedAt() == null ? null : item.getCreatedAt().toLocalDate())
                    .reference("IMP-" + item.getImportId())
                    .source("IMPORT")
                    .transactionType("IMPORT")
                    .channel(item.getShippingMethod())
                    .memberId(null)
                    .memberName(null)
                    .grossAmount(base)
                    .feeAmount(item.getShippingFee())
                    .taxAmount(taxes)
                    .netAmount(item.getTotalLandedCost())
                    .description("Supplier: " + item.getSupplierName()
                            + (item.getSupplierCountry() == null ? "" : " (" + item.getSupplierCountry() + ")"))
                    .build());
        }

        String typeFilter = transactionType == null ? null : transactionType.trim().toUpperCase(Locale.ROOT);
        String channelFilter = channel == null ? null : channel.trim().toUpperCase(Locale.ROOT);

        return rows.stream()
                .filter(row -> withinRange(row.getDate(), fromDate, toDate))
                .filter(row -> typeFilter == null || typeFilter.isEmpty() || "ALL".equals(typeFilter)
                        || row.getTransactionType().toUpperCase(Locale.ROOT).contains(typeFilter))
                .filter(row -> channelFilter == null || channelFilter.isEmpty() || "ALL".equals(channelFilter)
                        || row.getChannel().toUpperCase(Locale.ROOT).equals(channelFilter))
                .sorted(Comparator.comparing(TransactionSheetRow::getDate,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private boolean withinRange(LocalDate date, LocalDate fromDate, LocalDate toDate) {
        if (date == null) {
            return fromDate == null && toDate == null;
        }
        if (fromDate != null && date.isBefore(fromDate)) {
            return false;
        }
        return toDate == null || !date.isAfter(toDate);
    }

    private BigDecimal weightedRate(BigDecimal collected, BigDecimal taxable) {
        if (taxable == null || taxable.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return collected.multiply(new BigDecimal("100")).divide(taxable, 4, RoundingMode.HALF_UP);
    }

    private String csvValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private String plain(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static final class TaxAccumulator {
        private final String name;
        private final String code;
        private final String category;
        private final BigDecimal rate;
        private BigDecimal taxable = BigDecimal.ZERO;
        private BigDecimal collected = BigDecimal.ZERO;
        private long count;

        private TaxAccumulator(String name, String code, String category, BigDecimal rate) {
            this.name = name;
            this.code = code;
            this.category = category;
            this.rate = rate;
        }

        private TaxAccumulator(String name, String code, String category, BigDecimal rate,
                BigDecimal taxable, BigDecimal collected, long count) {
            this(name, code, category, rate);
            this.taxable = taxable;
            this.collected = collected;
            this.count = count;
        }
    }
}
