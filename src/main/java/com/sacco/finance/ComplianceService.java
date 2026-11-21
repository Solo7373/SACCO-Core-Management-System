package com.sacco.finance;

import com.sacco.account.entity.AccountHolder;
import com.sacco.account.repository.AccountHolderRepository;
import com.sacco.audit.AuditEventRepository;
import com.sacco.audit.dto.AuditEventResponse;
import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import com.sacco.finance.dto.TransactionSheetResponse;
import com.sacco.finance.dto.TransactionSheetRow;
import com.sacco.inventory.entity.ProductImport;
import com.sacco.inventory.repository.ProductImportRepository;
import com.sacco.loan.entity.Loan;
import com.sacco.loan.entity.LoanRepayment;
import com.sacco.loan.repository.LoanRepaymentRepository;
import com.sacco.loan.repository.LoanRepository;
import com.sacco.member.entity.Member;
import com.sacco.member.repository.MemberRepository;
import com.sacco.savings.entity.SavingsTransaction;
import com.sacco.savings.repository.SavingsTransactionRepository;
import com.sacco.share.entity.ShareTransaction;
import com.sacco.share.repository.ShareTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the auditor-only transaction spreadsheet and detailed audit log.
 * Read-only by design: no method in this service mutates financial data.
 */
@Service
@RequiredArgsConstructor
public class ComplianceService {

    private final SavingsTransactionRepository savingsTransactionRepository;
    private final AccountHolderRepository accountHolderRepository;
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
        BigDecimal totalNet = BigDecimal.ZERO;
        for (TransactionSheetRow row : rows) {
            totalGross = totalGross.add(row.getGrossAmount());
            totalNet = totalNet.add(row.getNetAmount());
        }

        return TransactionSheetResponse.builder()
                .rows(rows)
                .count(rows.size())
                .totalGross(totalGross)
                .totalNet(totalNet)
                .build();
    }

    @Transactional(readOnly = true)
    public String getTransactionSheetCsv(LocalDate fromDate, LocalDate toDate,
            String transactionType, String channel, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        TransactionSheetResponse sheet = getTransactionSheet(fromDate, toDate, transactionType, channel, actorId);
        StringBuilder csv = new StringBuilder();
        csv.append("Date,Reference,Source,Transaction Type,Channel,Member,Amount,Net Amount,Description\n");
        for (TransactionSheetRow row : sheet.getRows()) {
            csv.append(csvValue(row.getDate())).append(',')
                    .append(csvValue(row.getReference())).append(',')
                    .append(csvValue(row.getSource())).append(',')
                    .append(csvValue(row.getTransactionType())).append(',')
                    .append(csvValue(row.getChannel())).append(',')
                    .append(csvValue(row.getMemberName())).append(',')
                    .append(plain(row.getGrossAmount())).append(',')
                    .append(plain(row.getNetAmount())).append(',')
                    .append(csvValue(row.getDescription())).append('\n');
        }
        csv.append("TOTALS,,,,,,")
                .append(plain(sheet.getTotalGross())).append(',')
                .append(plain(sheet.getTotalNet())).append(",\n");
        return csv.toString();
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

        Map<Long, Long> accountMembers = new HashMap<>();
        for (AccountHolder holder : accountHolderRepository.findAll()) {
            Long current = accountMembers.get(holder.getAccountId());
            if (current == null || holder.getHolderType() == AccountHolder.HolderType.PRIMARY) {
                accountMembers.put(holder.getAccountId(), holder.getMemberId());
            }
        }

        List<TransactionSheetRow> rows = new ArrayList<>();
        for (SavingsTransaction tx : savingsTransactionRepository.findByStatus(
                SavingsTransaction.SavingsTransactionStatus.COMPLETED)) {
            Long memberId = accountMembers.get(tx.getAccountId());
            rows.add(TransactionSheetRow.builder()
                    .date(tx.getValueDate() != null ? tx.getValueDate()
                            : tx.getCreatedAt() == null ? null : tx.getCreatedAt().toLocalDate())
                    .reference(tx.getReference())
                    .source("SAVINGS")
                    .transactionType(tx.getTransactionType().name())
                    .channel(tx.getChannel())
                    .memberId(memberId)
                    .memberName(memberId == null ? null : memberNames.get(memberId))
                    .grossAmount(tx.getAmount())
                    .netAmount(tx.getNetAmount() == null ? tx.getAmount() : tx.getNetAmount())
                    .description(tx.getDescription())
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
                    .netAmount(tx.getAmount())
                    .description(tx.getQuantity() + " shares @ " + tx.getUnitPrice())
                    .build());
        }

        for (ProductImport item : productImportRepository.findAllByOrderByCreatedAtDesc()) {
            BigDecimal base = item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity()));
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
                        || (row.getChannel() != null
                                && row.getChannel().toUpperCase(Locale.ROOT).equals(channelFilter)))
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
}
