package com.sacco.dashboard;

import com.sacco.account.dto.AccountResponse;
import com.sacco.account.entity.Account;
import com.sacco.account.entity.AccountHolder;
import com.sacco.account.repository.AccountHolderRepository;
import com.sacco.account.repository.AccountRepository;
import com.sacco.audit.AuditEventRepository;
import com.sacco.audit.dto.AuditEventResponse;
import com.sacco.collateral.entity.CollateralAsset;
import com.sacco.collateral.repository.CollateralRepository;
import com.sacco.dashboard.dto.DashboardSummaryResponse;
import com.sacco.dashboard.dto.MonthlySavingsPoint;
import com.sacco.loan.entity.Loan;
import com.sacco.loan.repository.LoanRepository;
import com.sacco.member.dto.MemberResponse;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final MemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final SavingsTransactionRepository savingsTransactionRepository;
    private final ShareTransactionRepository shareTransactionRepository;
    private final AuditEventRepository auditEventRepository;
    private final CollateralRepository collateralRepository;
    private final LoanRepository loanRepository;

    private static final DateTimeFormatter MONTH_FORMAT =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary(LocalDate fromDate, LocalDate toDate, String granularity) {
        List<Member> members = memberRepository.findByIsDeletedFalse();
        List<Account> accounts = accountRepository.findAll();
        List<SavingsTransaction> savingsTransactions = savingsTransactionRepository.findAll();
        List<ShareTransaction> shareTransactions = shareTransactionRepository.findAll();

        long activeMembers = members.stream()
                .filter(m -> m.getStatus() == Member.MemberStatus.ACTIVE)
                .count();
        long pendingMembers = members.stream()
                .filter(m -> m.getStatus() == Member.MemberStatus.PENDING)
                .count();
        long suspendedMembers = members.stream()
                .filter(m -> m.getStatus() == Member.MemberStatus.SUSPENDED)
                .count();

        long activeAccounts = accounts.stream()
                .filter(a -> a.getStatus() == Account.AccountStatus.ACTIVE)
                .count();

        Map<String, Long> accountStatusBreakdown = accounts.stream()
                .collect(Collectors.groupingBy(a -> a.getStatus().name(), Collectors.counting()));

        BigDecimal totalSavings = savingsTransactions.stream()
                .filter(tx -> tx.getStatus() == SavingsTransaction.SavingsTransactionStatus.COMPLETED)
                .map(this::signedSavingsAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalShareCapital = shareTransactions.stream()
                .filter(tx -> tx.getStatus() == ShareTransaction.ShareTransactionStatus.COMPLETED)
                .map(this::signedShareAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Loan> activeLoanList = loanRepository.findByStatusIn(
                List.of(Loan.LoanStatus.DISBURSED, Loan.LoanStatus.IN_ARREARS));
        BigDecimal totalLoansOutstanding = activeLoanList.stream()
                .map(loan -> loan.getOutstandingPrincipal().add(loan.getOutstandingInterest()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long loansInArrears = activeLoanList.stream()
                .filter(loan -> loan.getStatus() == Loan.LoanStatus.IN_ARREARS)
                .count();

        // Assets remain part of the SACCO's collateral portfolio until they are released,
        // including those currently under default / auction.
        BigDecimal collateralHeldValue = collateralRepository
                .sumEstimatedValueExcludingStatus(CollateralAsset.CollateralStatus.RELEASED);
        if (collateralHeldValue == null) {
            collateralHeldValue = BigDecimal.ZERO;
        }
        long collateralHeldCount = collateralRepository
                .countByStatusNot(CollateralAsset.CollateralStatus.RELEASED);

        Comparator<Member> memberNewestFirst = Comparator.comparing(
                Member::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
        Comparator<Account> accountNewestFirst = Comparator.comparing(
                Account::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));

        return DashboardSummaryResponse.builder()
                .totalMembers(members.size())
                .activeMembers(activeMembers)
                .pendingMembers(pendingMembers)
                .suspendedMembers(suspendedMembers)
                .totalAccounts(accounts.size())
                .activeAccounts(activeAccounts)
                .inactiveAccounts(accounts.size() - activeAccounts)
                .accountStatusBreakdown(accountStatusBreakdown)
                .totalSavings(totalSavings)
                .totalShareCapital(totalShareCapital)
                .totalLoansOutstanding(totalLoansOutstanding)
                .activeLoans(activeLoanList.size())
                .loansInArrears(loansInArrears)
                .collateralHeldCount(collateralHeldCount)
                .collateralHeldValue(collateralHeldValue)
                .monthlySavings(buildSavingsOhlc(savingsTransactions, fromDate, toDate, granularity))
                .recentActivities(auditEventRepository.findTop100ByOrderByTimestampDesc().stream()
                        .limit(8)
                        .map(AuditEventResponse::fromEntity)
                        .toList())
                .recentMembers(members.stream()
                        .sorted(memberNewestFirst)
                        .limit(5)
                        .map(MemberResponse::fromEntity)
                        .toList())
                .recentAccounts(accounts.stream()
                        .sorted(accountNewestFirst)
                        .limit(5)
                        .map(AccountResponse::fromEntity)
                        .toList())
                .build();
    }

    public List<MonthlySavingsPoint> getSummaryMonthlySavings(LocalDate fromDate, LocalDate toDate, String granularity) {
        List<SavingsTransaction> savingsTransactions = savingsTransactionRepository.findAll();
        return buildSavingsOhlc(savingsTransactions, fromDate, toDate, granularity);
    }

    public List<MonthlySavingsPoint> getMemberMonthlySavings(Long memberId, LocalDate fromDate, LocalDate toDate, String granularity) {
        if (memberId == null) {
            return buildSavingsOhlc(List.of(), fromDate, toDate, granularity);
        }
        List<Long> accountIds = accountHolderRepository.findByMemberId(memberId).stream()
                .map(AccountHolder::getAccountId)
                .toList();
        if (accountIds.isEmpty()) {
            return buildSavingsOhlc(List.of(), fromDate, toDate, granularity);
        }
        List<SavingsTransaction> transactions = accountIds.stream()
                .flatMap(accountId -> savingsTransactionRepository.findByAccountId(accountId).stream())
                .toList();
        return buildSavingsOhlc(transactions, fromDate, toDate, granularity);
    }

    private List<MonthlySavingsPoint> buildMonthlySavings(List<SavingsTransaction> transactions) {
        return buildSavingsOhlc(transactions, null, null, "MONTHLY");
    }

    /**
     * Generalised OHLC aggregator. Supports DAILY, MONTHLY and YEARLY buckets
     * over an arbitrary date range so the same chart works for a single week,
     * a quarter, or a multi-year trend.
     */
    private List<MonthlySavingsPoint> buildSavingsOhlc(
            List<SavingsTransaction> transactions,
            LocalDate fromDate,
            LocalDate toDate,
            String granularity) {

        String mode = granularity == null ? "MONTHLY" : granularity.trim().toUpperCase();
        if (!List.of("DAILY", "MONTHLY", "YEARLY").contains(mode)) {
            mode = "MONTHLY";
        }

        LocalDate today = LocalDate.now();
        LocalDate start = fromDate == null ? today.minusMonths(11).withDayOfMonth(1) : fromDate;
        LocalDate end = toDate == null ? today : toDate;
        if (end.isBefore(start)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }

        // Build the ordered list of bucket keys (chronological).
        List<String> bucketKeys = new ArrayList<>();
        Map<String, LocalDate> bucketStart = new LinkedHashMap<>();
        if ("DAILY".equals(mode)) {
            LocalDate cursor = start;
            while (!cursor.isAfter(end)) {
                String key = cursor.toString();
                bucketKeys.add(key);
                bucketStart.put(key, cursor);
                cursor = cursor.plusDays(1);
            }
        } else if ("YEARLY".equals(mode)) {
            int y = start.getYear();
            while (y <= end.getYear()) {
                String key = String.valueOf(y);
                bucketKeys.add(key);
                bucketStart.put(key, LocalDate.of(y, 1, 1));
                y++;
            }
        } else {
            YearMonth cursor = YearMonth.from(start);
            YearMonth last = YearMonth.from(end);
            while (!cursor.isAfter(last)) {
                String key = cursor.toString();
                bucketKeys.add(key);
                bucketStart.put(key, cursor.atDay(1));
                cursor = cursor.plusMonths(1);
            }
        }

        Map<String, List<SavingsTransaction>> buckets = new LinkedHashMap<>();
        for (String key : bucketKeys) {
            buckets.put(key, new ArrayList<>());
        }

        BigDecimal runningBalance = BigDecimal.ZERO;
        List<SavingsTransaction> completed = transactions.stream()
                .filter(tx -> tx.getStatus() == SavingsTransaction.SavingsTransactionStatus.COMPLETED
                        && tx.getCreatedAt() != null)
                .sorted(Comparator.comparing(SavingsTransaction::getCreatedAt))
                .toList();

        for (SavingsTransaction tx : completed) {
            LocalDate date = tx.getCreatedAt().toLocalDate();
            if (date.isBefore(start)) {
                runningBalance = runningBalance.add(signedSavingsAmount(tx));
            } else if (!date.isAfter(end)) {
                String key = bucketKeyFor(date, mode);
                List<SavingsTransaction> bucket = buckets.get(key);
                if (bucket != null) {
                    bucket.add(tx);
                }
            }
        }

        DateTimeFormatter labelFormat = labelFormatFor(mode, start, end);

        List<MonthlySavingsPoint> points = new ArrayList<>();
        for (String key : bucketKeys) {
            LocalDate bs = bucketStart.get(key);
            BigDecimal open = runningBalance;
            BigDecimal high = open;
            BigDecimal low = open;
            BigDecimal deposits = BigDecimal.ZERO;
            BigDecimal withdrawals = BigDecimal.ZERO;

            for (SavingsTransaction tx : buckets.get(key)) {
                BigDecimal signed = signedSavingsAmount(tx);
                if (signed.signum() >= 0) {
                    deposits = deposits.add(creditedAmount(tx));
                } else {
                    withdrawals = withdrawals.add(tx.getAmount());
                }
                runningBalance = runningBalance.add(signed);
                high = high.max(runningBalance);
                low = low.min(runningBalance);
            }

            BigDecimal close = runningBalance;
            high = high.max(close);
            low = low.min(close);

            points.add(MonthlySavingsPoint.builder()
                    .month(bs.format(labelFormat))
                    .deposits(deposits)
                    .withdrawals(withdrawals)
                    .net(close.subtract(open))
                    .cumulative(close)
                    .open(open)
                    .high(high)
                    .low(low)
                    .close(close)
                    .build());
        }
        return points;
    }

    private String bucketKeyFor(LocalDate date, String mode) {
        if ("DAILY".equals(mode)) {
            return date.toString();
        }
        if ("YEARLY".equals(mode)) {
            return String.valueOf(date.getYear());
        }
        return YearMonth.from(date).toString();
    }

    private DateTimeFormatter labelFormatFor(String mode, LocalDate start, LocalDate end) {
        if ("DAILY".equals(mode)) {
            boolean multiYear = start.getYear() != end.getYear();
            return DateTimeFormatter.ofPattern(multiYear ? "dd MMM yy" : "dd MMM", Locale.ENGLISH);
        }
        if ("YEARLY".equals(mode)) {
            return DateTimeFormatter.ofPattern("yyyy", Locale.ENGLISH);
        }
        return DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    }

    private BigDecimal signedSavingsAmount(SavingsTransaction tx) {
        return isSavingsCredit(tx.getTransactionType())
                ? creditedAmount(tx)
                : tx.getAmount().negate();
    }

    /** Uses the stored net amount so historical postings remain unchanged. */
    private BigDecimal creditedAmount(SavingsTransaction tx) {
        return tx.getNetAmount() == null ? tx.getAmount() : tx.getNetAmount();
    }

    private boolean isSavingsCredit(SavingsTransaction.SavingsTransactionType type) {
        return type == SavingsTransaction.SavingsTransactionType.DEPOSIT
                || type == SavingsTransaction.SavingsTransactionType.TRANSFER_IN
                || type == SavingsTransaction.SavingsTransactionType.INTEREST;
    }

    private BigDecimal signedShareAmount(ShareTransaction tx) {
        return switch (tx.getTransactionType()) {
            case PURCHASE, TRANSFER_IN -> tx.getAmount();
            case TRANSFER_OUT, REDEMPTION -> tx.getAmount().negate();
        };
    }
}
