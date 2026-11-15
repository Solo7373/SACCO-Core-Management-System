package com.sacco.report;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sacco.account.entity.Account;
import com.sacco.account.entity.AccountHolder;
import com.sacco.account.repository.AccountHolderRepository;
import com.sacco.account.repository.AccountRepository;
import com.sacco.ledger.dto.TrialBalanceResponse;
import com.sacco.ledger.entity.ChartOfAccounts;
import com.sacco.ledger.entity.JournalEntry;
import com.sacco.ledger.entity.JournalLine;
import com.sacco.ledger.repository.ChartOfAccountsRepository;
import com.sacco.ledger.repository.JournalLineRepository;
import com.sacco.loan.entity.Loan;
import com.sacco.loan.entity.LoanProduct;
import com.sacco.loan.entity.LoanRepayment;
import com.sacco.loan.repository.LoanProductRepository;
import com.sacco.loan.repository.LoanRepaymentRepository;
import com.sacco.loan.repository.LoanRepository;
import com.sacco.member.entity.Member;
import com.sacco.member.repository.MemberRepository;
import com.sacco.report.dto.BalanceSheetResponse;
import com.sacco.report.dto.FinancialLineResponse;
import com.sacco.report.dto.IncomeExpenditureResponse;
import com.sacco.savings.SavingsService;
import com.sacco.savings.entity.SavingsTransaction;
import com.sacco.savings.repository.SavingsTransactionRepository;
import com.sacco.share.entity.ShareProduct;
import com.sacco.share.entity.ShareTransaction;
import com.sacco.share.repository.ShareProductRepository;
import com.sacco.share.repository.ShareTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportingService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    private final AccountRepository accountRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final SavingsTransactionRepository savingsTransactionRepository;
    private final ShareTransactionRepository shareTransactionRepository;
    private final ShareProductRepository shareProductRepository;
    private final LoanRepository loanRepository;
    private final LoanRepaymentRepository loanRepaymentRepository;
    private final LoanProductRepository loanProductRepository;
    private final MemberRepository memberRepository;
    private final SavingsService savingsService;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final JournalLineRepository journalLineRepository;

    @Transactional(readOnly = true)
    public List<TrialBalanceResponse> getTrialBalance(LocalDate asOfDate) {
        LocalDate asOf = asOfDate == null ? LocalDate.now() : asOfDate;
        Position position = computePosition(asOf, null);
        List<TrialBalanceResponse> rows = new ArrayList<>();

        addSigned(rows, "1000", "Cash & Bank", "ASSET", position.cash, true);
        addSigned(rows, "1200", "Loans Receivable", "ASSET", position.loansReceivable, true);
        addSigned(rows, "1210", "Interest Receivable", "ASSET", position.interestReceivable, true);
        position.expenses.forEach((code, amount) ->
                addSigned(rows, code, accountName(code), "EXPENSE", amount, true));
        addSigned(rows, "2000", "Member Savings Liability", "LIABILITY", position.savings, false);
        addSigned(rows, "3000", "Share Capital", "EQUITY", position.shareCapital, false);
        addSigned(rows, "4000", "Interest Income", "INCOME", position.interestIncome, false);
        addSigned(rows, "4200", "Penalty Income", "INCOME", position.penaltyIncome, false);
        position.otherIncome.forEach((code, amount) ->
                addSigned(rows, code, accountName(code), "INCOME", amount, false));

        return rows.stream().filter(row -> row.getTotalDebit().compareTo(BigDecimal.ZERO) > 0
                || row.getTotalCredit().compareTo(BigDecimal.ZERO) > 0).toList();
    }

    @Transactional(readOnly = true)
    public BalanceSheetResponse getBalanceSheet(LocalDate asOfDate) {
        LocalDate asOf = asOfDate == null ? LocalDate.now() : asOfDate;
        Position position = computePosition(asOf, null);

        List<FinancialLineResponse> assets = new ArrayList<>();
        assets.add(line("1000", "Cash & Bank", position.cash));
        assets.add(line("1200", "Loans Receivable", position.loansReceivable));
        assets.add(line("1210", "Interest Receivable", position.interestReceivable));
        BigDecimal totalAssets = assets.stream().map(FinancialLineResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<FinancialLineResponse> liabilities = new ArrayList<>();
        liabilities.add(line("2000", "Member Savings", position.savings));
        BigDecimal totalLiabilities = position.savings;

        List<FinancialLineResponse> equity = new ArrayList<>();
        equity.add(line("3000", "Share Capital", position.shareCapital));
        equity.add(line("3100", "Retained Earnings / Net Surplus", position.netSurplus));
        BigDecimal totalEquity = equity.stream().map(FinancialLineResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalLiabilitiesAndEquity = totalLiabilities.add(totalEquity);
        return BalanceSheetResponse.builder()
                .asOfDate(asOf)
                .assets(assets)
                .totalAssets(totalAssets)
                .liabilities(liabilities)
                .totalLiabilities(totalLiabilities)
                .equity(equity)
                .totalEquity(totalEquity)
                .netSurplus(position.netSurplus)
                .totalLiabilitiesAndEquity(totalLiabilitiesAndEquity)
                .balanced(totalAssets.compareTo(totalLiabilitiesAndEquity) == 0)
                .build();
    }

    @Transactional(readOnly = true)
    public IncomeExpenditureResponse getIncomeExpenditure(LocalDate fromDate, LocalDate toDate) {
        LocalDate to = toDate == null ? LocalDate.now() : toDate;
        LocalDate from = fromDate == null ? to.withDayOfMonth(1) : fromDate;
        Position position = computePosition(to, from);

        List<FinancialLineResponse> income = new ArrayList<>();
        income.add(line("4000", "Interest Income", position.interestIncome));
        income.add(line("4200", "Penalty Income", position.penaltyIncome));
        position.otherIncome.forEach((code, amount) -> income.add(line(code, accountName(code), amount)));

        List<FinancialLineResponse> expenses = new ArrayList<>();
        position.expenses.forEach((code, amount) -> expenses.add(line(code, accountName(code), amount)));

        BigDecimal totalIncome = income.stream().map(FinancialLineResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpenses = expenses.stream().map(FinancialLineResponse::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return IncomeExpenditureResponse.builder()
                .fromDate(from)
                .toDate(to)
                .income(income)
                .totalIncome(totalIncome)
                .expenses(expenses)
                .totalExpenses(totalExpenses)
                .netSurplus(totalIncome.subtract(totalExpenses))
                .build();
    }

    @Transactional(readOnly = true)
    public byte[] getMemberStatementCsv(Long memberId) {
        MemberStatement statement = buildMemberStatement(memberId);
        StringBuilder sb = new StringBuilder();
        appendRow(sb, "SACCO CORE MANAGEMENT SYSTEM - MEMBER STATEMENT");
        appendRow(sb, "Generated", DATETIME_FORMAT.format(statement.generatedAt));
        appendRow(sb, "Member", statement.memberName);
        appendRow(sb, "Membership No", statement.membershipNo);
        appendRow(sb, "Phone", statement.phone);
        appendRow(sb, "Savings Balance", money(statement.savingsBalance));
        appendRow(sb, "Share Value", money(statement.shareValue));
        appendRow(sb, "Loan Outstanding", money(statement.loanOutstanding));
        appendRow(sb);

        appendRow(sb, "ACCOUNTS");
        appendRow(sb, "Account Number", "Type", "Status", "Balance");
        for (AccountRow row : statement.accounts) {
            appendRow(sb, row.accountNumber, row.accountType, row.status, money(row.balance));
        }
        appendRow(sb);

        appendRow(sb, "SAVINGS TRANSACTIONS");
        appendRow(sb, "Date", "Reference", "Account", "Type", "Amount", "Status", "Narration");
        for (SavingsRow row : statement.savingsTransactions) {
            appendRow(sb, row.date, row.reference, row.accountNumber, row.type,
                    money(row.amount), row.status, row.description);
        }
        appendRow(sb);

        appendRow(sb, "SHARE TRANSACTIONS");
        appendRow(sb, "Date", "Reference", "Product", "Type", "Quantity", "Unit Price", "Amount", "Status");
        for (ShareRow row : statement.shareTransactions) {
            appendRow(sb, row.date, row.reference, row.productName, row.type,
                    row.quantity, money(row.unitPrice), money(row.amount), row.status);
        }
        appendRow(sb);

        appendRow(sb, "LOANS");
        appendRow(sb, "Loan Number", "Product", "Amount", "Status", "Outstanding Principal",
                "Outstanding Interest", "Total Paid", "Applied");
        for (LoanRow row : statement.loans) {
            appendRow(sb, row.loanNumber, row.productName, money(row.amount), row.status,
                    money(row.outstandingPrincipal), money(row.outstandingInterest),
                    money(row.totalPaid), row.applicationDate);
        }
        appendRow(sb);

        appendRow(sb, "LOAN REPAYMENTS");
        appendRow(sb, "Date", "Reference", "Loan", "Amount", "Principal", "Interest", "Penalty");
        for (RepaymentRow row : statement.repayments) {
            appendRow(sb, row.date, row.reference, row.loanNumber, money(row.amount),
                    money(row.principal), money(row.interest), money(row.penalty));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public byte[] getMemberStatementPdf(Long memberId) {
        MemberStatement statement = buildMemberStatement(memberId);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 32, 32, 32, 32);
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, new Color(230, 81, 0));
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.DARK_GRAY);
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(191, 54, 12));
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);

            Paragraph title = new Paragraph("SACCO Core Management System", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            Paragraph subtitle = new Paragraph("Member Financial Statement", subtitleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(14);
            document.add(subtitle);

            document.add(new Paragraph("Member: " + statement.memberName
                    + "   |   Membership No: " + statement.membershipNo
                    + "   |   Generated: " + DATETIME_FORMAT.format(statement.generatedAt), bodyFont));
            document.add(new Paragraph("Savings Balance: " + money(statement.savingsBalance)
                    + "   |   Share Value: " + money(statement.shareValue)
                    + "   |   Loan Outstanding: " + money(statement.loanOutstanding), bodyFont));
            document.add(new Paragraph(" ", bodyFont));

            addSection(document, "Accounts", sectionFont);
            PdfPTable accountTable = table(4);
            addHeader(accountTable, headerFont, "Account Number", "Type", "Status", "Balance");
            for (AccountRow row : statement.accounts) {
                addCell(accountTable, bodyFont, row.accountNumber);
                addCell(accountTable, bodyFont, row.accountType);
                addCell(accountTable, bodyFont, row.status);
                addCell(accountTable, bodyFont, money(row.balance));
            }
            document.add(accountTable);

            addSection(document, "Savings Transactions", sectionFont);
            PdfPTable savingsTable = table(6);
            addHeader(savingsTable, headerFont, "Date", "Reference", "Account", "Type", "Amount", "Status");
            for (SavingsRow row : statement.savingsTransactions) {
                addCell(savingsTable, bodyFont, row.date);
                addCell(savingsTable, bodyFont, row.reference);
                addCell(savingsTable, bodyFont, row.accountNumber);
                addCell(savingsTable, bodyFont, row.type);
                addCell(savingsTable, bodyFont, money(row.amount));
                addCell(savingsTable, bodyFont, row.status);
            }
            document.add(savingsTable);

            addSection(document, "Share Transactions", sectionFont);
            PdfPTable shareTable = table(7);
            addHeader(shareTable, headerFont, "Date", "Reference", "Product", "Type", "Qty", "Unit Price", "Amount");
            for (ShareRow row : statement.shareTransactions) {
                addCell(shareTable, bodyFont, row.date);
                addCell(shareTable, bodyFont, row.reference);
                addCell(shareTable, bodyFont, row.productName);
                addCell(shareTable, bodyFont, row.type);
                addCell(shareTable, bodyFont, String.valueOf(row.quantity));
                addCell(shareTable, bodyFont, money(row.unitPrice));
                addCell(shareTable, bodyFont, money(row.amount));
            }
            document.add(shareTable);

            addSection(document, "Loans", sectionFont);
            PdfPTable loanTable = table(6);
            addHeader(loanTable, headerFont, "Loan Number", "Product", "Amount", "Status", "Outstanding", "Total Paid");
            for (LoanRow row : statement.loans) {
                addCell(loanTable, bodyFont, row.loanNumber);
                addCell(loanTable, bodyFont, row.productName);
                addCell(loanTable, bodyFont, money(row.amount));
                addCell(loanTable, bodyFont, row.status);
                addCell(loanTable, bodyFont, money(row.outstandingPrincipal.add(row.outstandingInterest)));
                addCell(loanTable, bodyFont, money(row.totalPaid));
            }
            document.add(loanTable);

            addSection(document, "Loan Repayments", sectionFont);
            PdfPTable repaymentTable = table(6);
            addHeader(repaymentTable, headerFont, "Date", "Reference", "Loan", "Amount", "Interest", "Penalty");
            for (RepaymentRow row : statement.repayments) {
                addCell(repaymentTable, bodyFont, row.date);
                addCell(repaymentTable, bodyFont, row.reference);
                addCell(repaymentTable, bodyFont, row.loanNumber);
                addCell(repaymentTable, bodyFont, money(row.amount));
                addCell(repaymentTable, bodyFont, money(row.interest));
                addCell(repaymentTable, bodyFont, money(row.penalty));
            }
            document.add(repaymentTable);

            document.close();
            return out.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not generate member statement PDF: " + ex.getMessage(), ex);
        }
    }

    private Position computePosition(LocalDate asOf, LocalDate fromDate) {
        Position position = new Position();

        Map<Long, Account> accounts = accountRepository.findAll().stream()
                .collect(Collectors.toMap(Account::getAccountId, account -> account, (a, b) -> a));
        for (SavingsTransaction tx : savingsTransactionRepository.findAll()) {
            if (tx.getStatus() != SavingsTransaction.SavingsTransactionStatus.COMPLETED) {
                continue;
            }
            LocalDate date = tx.getValueDate() == null ? asOf : tx.getValueDate();
            if (date.isAfter(asOf) || (fromDate != null && date.isBefore(fromDate))) {
                continue;
            }
            BigDecimal signed = isSavingsCredit(tx.getTransactionType()) ? tx.getAmount() : tx.getAmount().negate();
            position.savings = position.savings.add(signed);
        }

        for (ShareTransaction tx : shareTransactionRepository.findAll()) {
            if (tx.getStatus() != ShareTransaction.ShareTransactionStatus.COMPLETED) {
                continue;
            }
            LocalDate date = tx.getTransactionDate() == null ? asOf : tx.getTransactionDate();
            if (date.isAfter(asOf)) {
                continue;
            }
            BigDecimal signed = isShareCredit(tx.getTransactionType()) ? tx.getAmount() : tx.getAmount().negate();
            position.shareCapital = position.shareCapital.add(signed);
        }

        for (Loan loan : loanRepository.findAll()) {
            if (loan.getStatus() != Loan.LoanStatus.DISBURSED && loan.getStatus() != Loan.LoanStatus.IN_ARREARS) {
                continue;
            }
            position.loansReceivable = position.loansReceivable.add(loan.getOutstandingPrincipal());
            position.interestReceivable = position.interestReceivable.add(loan.getOutstandingInterest());
        }

        for (LoanRepayment repayment : loanRepaymentRepository.findAll()) {
            LocalDate date = repayment.getPaymentDate() == null ? asOf : repayment.getPaymentDate();
            if (date.isAfter(asOf) || (fromDate != null && date.isBefore(fromDate))) {
                continue;
            }
            position.interestIncome = position.interestIncome.add(repayment.getInterestPortion());
            position.penaltyIncome = position.penaltyIncome.add(repayment.getPenaltyPortion());
        }

        Map<String, ChartOfAccounts> chart = chartOfAccountsRepository.findAll().stream()
                .collect(Collectors.toMap(ChartOfAccounts::getAccountCode, account -> account, (a, b) -> a));
        for (JournalLine line : journalLineRepository.findAll()) {
            JournalEntry entry = line.getJournalEntry();
            if (entry == null || entry.getStatus() != JournalEntry.JournalEntryStatus.POSTED) {
                continue;
            }
            LocalDate date = entry.getEntryDate() == null ? asOf : entry.getEntryDate();
            if (date.isAfter(asOf) || (fromDate != null && date.isBefore(fromDate))) {
                continue;
            }
            ChartOfAccounts account = chart.get(line.getAccountCode());
            if (account == null) {
                continue;
            }
            if (account.getCategory() == ChartOfAccounts.AccountCategory.EXPENSE) {
                BigDecimal amount = line.getDebit().subtract(line.getCredit());
                position.expenses.merge(line.getAccountCode(), amount, BigDecimal::add);
            } else if (account.getCategory() == ChartOfAccounts.AccountCategory.INCOME
                    && !"4000".equals(line.getAccountCode())
                    && !"4200".equals(line.getAccountCode())) {
                BigDecimal amount = line.getCredit().subtract(line.getDebit());
                position.otherIncome.merge(line.getAccountCode(), amount, BigDecimal::add);
            }
        }

        BigDecimal otherIncomeTotal = position.otherIncome.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expenseTotal = position.expenses.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        position.netSurplus = position.interestIncome.add(position.penaltyIncome)
                .add(otherIncomeTotal).subtract(expenseTotal);
        position.cash = position.savings.add(position.shareCapital).add(position.netSurplus)
                .subtract(position.loansReceivable).subtract(position.interestReceivable);
        return position;
    }

    private MemberStatement buildMemberStatement(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + memberId));
        List<Long> accountIds = accountHolderRepository.findByMemberId(memberId).stream()
                .map(AccountHolder::getAccountId)
                .toList();
        List<Account> accounts = accountIds.isEmpty() ? List.of() : accountRepository.findAllById(accountIds);
        Map<Long, Account> accountMap = accounts.stream()
                .collect(Collectors.toMap(Account::getAccountId, account -> account, (a, b) -> a));

        MemberStatement statement = new MemberStatement();
        statement.generatedAt = LocalDateTime.now();
        statement.memberName = (member.getFirstName() + " " + member.getLastName()).trim();
        statement.membershipNo = member.getMembershipNo();
        statement.phone = member.getPhone();

        BigDecimal savingsBalance = BigDecimal.ZERO;
        for (Account account : accounts) {
            BigDecimal balance = savingsService.calculateBalance(account.getAccountId());
            if (account.getAccountType() == Account.AccountType.SAVINGS
                    || account.getAccountType() == Account.AccountType.FIXED_DEPOSIT) {
                savingsBalance = savingsBalance.add(balance);
            }
            statement.accounts.add(new AccountRow(account.getAccountNumber(),
                    account.getAccountType().name(), account.getStatus().name(), balance));
        }
        statement.savingsBalance = savingsBalance;

        Map<Long, String> productNames = shareProductRepository.findAll().stream()
                .collect(Collectors.toMap(ShareProduct::getProductId, ShareProduct::getName, (a, b) -> a));
        BigDecimal shareValue = BigDecimal.ZERO;
        for (ShareTransaction tx : shareTransactionRepository.findByMemberId(memberId)) {
            if (tx.getStatus() != ShareTransaction.ShareTransactionStatus.COMPLETED) {
                continue;
            }
            BigDecimal signed = isShareCredit(tx.getTransactionType()) ? tx.getAmount() : tx.getAmount().negate();
            shareValue = shareValue.add(signed);
            statement.shareTransactions.add(new ShareRow(
                    tx.getTransactionDate() == null ? "" : DATE_FORMAT.format(tx.getTransactionDate()),
                    tx.getReference(), productNames.getOrDefault(tx.getProductId(), "Product #" + tx.getProductId()),
                    tx.getTransactionType().name(), tx.getQuantity(), tx.getUnitPrice(), tx.getAmount(),
                    tx.getStatus().name()));
        }
        statement.shareValue = shareValue;
        statement.shareTransactions.sort(Comparator.comparing(ShareRow::date).reversed());

        for (Account account : accounts) {
            for (SavingsTransaction tx : savingsTransactionRepository.findByAccountId(account.getAccountId())) {
                statement.savingsTransactions.add(new SavingsRow(
                        tx.getValueDate() == null ? "" : DATE_FORMAT.format(tx.getValueDate()),
                        tx.getReference(), account.getAccountNumber(), tx.getTransactionType().name(),
                        tx.getAmount(), tx.getStatus().name(), tx.getDescription()));
            }
        }
        statement.savingsTransactions.sort(Comparator.comparing(SavingsRow::date).reversed());

        Map<Long, String> loanProductNames = loanProductRepository.findAll().stream()
                .collect(Collectors.toMap(LoanProduct::getProductId, LoanProduct::getName, (a, b) -> a));
        Map<Long, String> loanNumbers = new HashMap<>();
        BigDecimal loanOutstanding = BigDecimal.ZERO;
        for (Loan loan : loanRepository.findByMemberIdOrderByCreatedAtDesc(memberId)) {
            loanNumbers.put(loan.getLoanId(), loan.getLoanNumber());
            if (loan.getStatus() == Loan.LoanStatus.DISBURSED || loan.getStatus() == Loan.LoanStatus.IN_ARREARS) {
                loanOutstanding = loanOutstanding.add(loan.getOutstandingPrincipal())
                        .add(loan.getOutstandingInterest());
            }
            statement.loans.add(new LoanRow(loan.getLoanNumber(),
                    loanProductNames.getOrDefault(loan.getProductId(), "Product #" + loan.getProductId()),
                    loan.getAmount(), loan.getStatus().name(), loan.getOutstandingPrincipal(),
                    loan.getOutstandingInterest(), loan.getTotalPaid(),
                    loan.getApplicationDate() == null ? "" : DATE_FORMAT.format(loan.getApplicationDate())));
            for (LoanRepayment repayment : loanRepaymentRepository.findByLoanIdOrderByCreatedAtDesc(loan.getLoanId())) {
                statement.repayments.add(new RepaymentRow(
                        repayment.getPaymentDate() == null ? "" : DATE_FORMAT.format(repayment.getPaymentDate()),
                        repayment.getReference(), loan.getLoanNumber(), repayment.getAmount(),
                        repayment.getPrincipalPortion(), repayment.getInterestPortion(), repayment.getPenaltyPortion()));
            }
        }
        statement.loanOutstanding = loanOutstanding;
        statement.repayments.sort(Comparator.comparing(RepaymentRow::date).reversed());
        return statement;
    }

    private void addSigned(List<TrialBalanceResponse> rows, String code, String name, String category,
            BigDecimal amount, boolean debitNormal) {
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        if (debitNormal) {
            if (normalized.compareTo(BigDecimal.ZERO) >= 0) {
                debit = normalized;
            } else {
                credit = normalized.negate();
            }
        } else {
            if (normalized.compareTo(BigDecimal.ZERO) >= 0) {
                credit = normalized;
            } else {
                debit = normalized.negate();
            }
        }
        rows.add(TrialBalanceResponse.builder()
                .accountCode(code)
                .accountName(name)
                .category(category)
                .totalDebit(debit)
                .totalCredit(credit)
                .netBalance(debit.subtract(credit))
                .generatedAt(LocalDate.now())
                .build());
    }

    private FinancialLineResponse line(String code, String name, BigDecimal amount) {
        return FinancialLineResponse.builder()
                .accountCode(code)
                .accountName(name)
                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                .build();
    }

    private String accountName(String code) {
        return chartOfAccountsRepository.findById(code).map(ChartOfAccounts::getName).orElse(code);
    }

    private boolean isSavingsCredit(SavingsTransaction.SavingsTransactionType type) {
        return type == SavingsTransaction.SavingsTransactionType.DEPOSIT
                || type == SavingsTransaction.SavingsTransactionType.TRANSFER_IN
                || type == SavingsTransaction.SavingsTransactionType.INTEREST;
    }

    private boolean isShareCredit(ShareTransaction.ShareTransactionType type) {
        return type == ShareTransaction.ShareTransactionType.PURCHASE
                || type == ShareTransaction.ShareTransactionType.TRANSFER_IN;
    }

    private String money(BigDecimal amount) {
        return "UGX " + (amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP)).toPlainString();
    }

    private void appendRow(StringBuilder sb, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(csv(values[i]));
        }
        sb.append("\r\n");
    }

    private String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private PdfPTable table(int columns) {
        PdfPTable table = new PdfPTable(columns);
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setSpacingAfter(10);
        return table;
    }

    private void addSection(Document document, String title, Font font) throws Exception {
        Paragraph paragraph = new Paragraph(title, font);
        paragraph.setSpacingBefore(10);
        paragraph.setSpacingAfter(4);
        document.add(paragraph);
    }

    private void addHeader(PdfPTable table, Font font, String... headers) {
        for (String header : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(header, font));
            cell.setBackgroundColor(new Color(230, 81, 0));
            cell.setPadding(4);
            table.addCell(cell);
        }
    }

    private void addCell(PdfPTable table, Font font, String value) {
        PdfPCell cell = new PdfPCell(new Phrase(value == null ? "" : value, font));
        cell.setPadding(3);
        table.addCell(cell);
    }

    private static class Position {
        private BigDecimal savings = BigDecimal.ZERO;
        private BigDecimal shareCapital = BigDecimal.ZERO;
        private BigDecimal loansReceivable = BigDecimal.ZERO;
        private BigDecimal interestReceivable = BigDecimal.ZERO;
        private BigDecimal interestIncome = BigDecimal.ZERO;
        private BigDecimal penaltyIncome = BigDecimal.ZERO;
        private BigDecimal netSurplus = BigDecimal.ZERO;
        private BigDecimal cash = BigDecimal.ZERO;
        private final Map<String, BigDecimal> otherIncome = new LinkedHashMap<>();
        private final Map<String, BigDecimal> expenses = new LinkedHashMap<>();
    }

    private static class MemberStatement {
        private LocalDateTime generatedAt;
        private String memberName;
        private String membershipNo;
        private String phone;
        private BigDecimal savingsBalance = BigDecimal.ZERO;
        private BigDecimal shareValue = BigDecimal.ZERO;
        private BigDecimal loanOutstanding = BigDecimal.ZERO;
        private final List<AccountRow> accounts = new ArrayList<>();
        private final List<SavingsRow> savingsTransactions = new ArrayList<>();
        private final List<ShareRow> shareTransactions = new ArrayList<>();
        private final List<LoanRow> loans = new ArrayList<>();
        private final List<RepaymentRow> repayments = new ArrayList<>();
    }

    private record AccountRow(String accountNumber, String accountType, String status, BigDecimal balance) {}
    private record SavingsRow(String date, String reference, String accountNumber, String type,
            BigDecimal amount, String status, String description) {}
    private record ShareRow(String date, String reference, String productName, String type,
            Integer quantity, BigDecimal unitPrice, BigDecimal amount, String status) {}
    private record LoanRow(String loanNumber, String productName, BigDecimal amount, String status,
            BigDecimal outstandingPrincipal, BigDecimal outstandingInterest, BigDecimal totalPaid,
            String applicationDate) {}
    private record RepaymentRow(String date, String reference, String loanNumber, BigDecimal amount,
            BigDecimal principal, BigDecimal interest, BigDecimal penalty) {}
}
