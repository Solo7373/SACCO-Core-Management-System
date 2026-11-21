package com.sacco.savings;

import com.sacco.account.AccountService;
import com.sacco.account.entity.Account;
import com.sacco.account.repository.AccountRepository;
import com.sacco.audit.AuditService;
import com.sacco.common.ReferenceNumberService;
import com.sacco.savings.dto.SavingsTransactionRequest;
import com.sacco.savings.dto.SavingsTransactionResponse;
import com.sacco.savings.dto.SavingsTransferRequest;
import com.sacco.savings.dto.SavingsTransferResponse;
import com.sacco.savings.entity.SavingsTransaction;
import com.sacco.savings.repository.SavingsTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class SavingsService {

    private final SavingsTransactionRepository savingsTransactionRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final ReferenceNumberService referenceNumberService;
    private final AuditService auditService;

    @Value("${sacco.savings.withdrawal-approval-threshold:100000}")
    private BigDecimal withdrawalApprovalThreshold;

    @Transactional
    public SavingsTransactionResponse postTransaction(SavingsTransactionRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        Account account = findActiveAccount(request.getAccountId());
        boolean isDebit = isDebit(request.getTransactionType());
        String channel = normalizeChannel(request.getChannel(), "CASH");

        if (isDebit) {
            BigDecimal balance = calculateBalance(request.getAccountId());
            if (balance.compareTo(request.getAmount()) < 0) {
                throw new IllegalStateException("Insufficient funds. Available: " + balance
                        + ", Requested: " + request.getAmount());
            }
            requireApprovalForLargeDebit(request.getTransactionType(), request.getAmount(),
                    request.getAuthorizedBy());
        }

        LocalDateTime now = LocalDateTime.now();
        SavingsTransaction transaction = SavingsTransaction.builder()
                .accountId(request.getAccountId())
                .reference(referenceNumberService.next("TXN"))
                .transactionType(request.getTransactionType())
                .amount(request.getAmount())
                .currency(request.getCurrency() == null ? account.getCurrency() : request.getCurrency())
                .status(SavingsTransaction.SavingsTransactionStatus.COMPLETED)
                .externalReference(trimToNull(request.getExternalReference()))
                .authorizedBy(trimToNull(request.getAuthorizedBy()))
                .approvalReference(trimToNull(request.getApprovalReference()))
                .valueDate(request.getValueDate() == null ? LocalDate.now() : request.getValueDate())
                .description(trimToNull(request.getDescription()))
                .channel(channel)
                .feeAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .netAmount(request.getAmount())
                .postedBy(actorId)
                .build();

        SavingsTransaction saved = savingsTransactionRepository.save(transaction);
        accountService.recordActivity(account.getAccountId(), now);

        auditService.logEvent(actorId, "SAVINGS_TRANSACTION_POSTED", "SAVINGS_TRANSACTION",
                saved.getTransactionId(),
                "Ref: " + saved.getReference() + ", Type: " + request.getTransactionType()
                        + ", Channel: " + channel
                        + ", Amount: " + request.getAmount()
                        + (saved.getAuthorizedBy() == null ? "" : ", Authorized by: " + saved.getAuthorizedBy()));
        return SavingsTransactionResponse.fromEntity(saved);
    }

    @Transactional
    public SavingsTransferResponse postTransfer(SavingsTransferRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new IllegalStateException("Source and destination accounts must be different");
        }

        Account from = findActiveAccount(request.getFromAccountId());
        Account to = findActiveAccount(request.getToAccountId());
        if (!from.getCurrency().equals(to.getCurrency())) {
            throw new IllegalStateException("Source and destination accounts must use the same currency");
        }

        BigDecimal balance = calculateBalance(from.getAccountId());
        if (balance.compareTo(request.getAmount()) < 0) {
            throw new IllegalStateException("Insufficient funds. Available: " + balance
                    + ", Requested: " + request.getAmount());
        }
        requireApprovalForLargeDebit(SavingsTransaction.SavingsTransactionType.TRANSFER_OUT,
                request.getAmount(), request.getAuthorizedBy());

        String channel = normalizeChannel(request.getChannel(), "INTERNAL");

        String transferReference = referenceNumberService.next("TRF");
        LocalDateTime now = LocalDateTime.now();
        LocalDate valueDate = LocalDate.now();
        String description = trimToNull(request.getDescription());

        SavingsTransaction out = savingsTransactionRepository.save(SavingsTransaction.builder()
                .accountId(from.getAccountId())
                .reference(referenceNumberService.next("TXN"))
                .transactionType(SavingsTransaction.SavingsTransactionType.TRANSFER_OUT)
                .amount(request.getAmount())
                .currency(from.getCurrency())
                .status(SavingsTransaction.SavingsTransactionStatus.COMPLETED)
                .externalReference(transferReference)
                .authorizedBy(trimToNull(request.getAuthorizedBy()))
                .approvalReference(trimToNull(request.getApprovalReference()))
                .valueDate(valueDate)
                .description(description == null ? "Transfer to " + to.getAccountNumber() : description)
                .channel(channel)
                .feeAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .netAmount(request.getAmount())
                .postedBy(actorId)
                .build());

        SavingsTransaction in = savingsTransactionRepository.save(SavingsTransaction.builder()
                .accountId(to.getAccountId())
                .reference(referenceNumberService.next("TXN"))
                .transactionType(SavingsTransaction.SavingsTransactionType.TRANSFER_IN)
                .amount(request.getAmount())
                .currency(to.getCurrency())
                .status(SavingsTransaction.SavingsTransactionStatus.COMPLETED)
                .externalReference(transferReference)
                .valueDate(valueDate)
                .description(description == null ? "Transfer from " + from.getAccountNumber() : description)
                .channel(channel)
                .feeAmount(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .netAmount(request.getAmount())
                .postedBy(actorId)
                .build());

        accountService.recordActivity(from.getAccountId(), now);
        accountService.recordActivity(to.getAccountId(), now);

        auditService.logEvent(actorId, "SAVINGS_TRANSFER_POSTED", "SAVINGS_TRANSACTION", out.getTransactionId(),
                "Ref: " + transferReference + ", From: " + from.getAccountNumber()
                        + ", To: " + to.getAccountNumber() + ", Amount: " + request.getAmount()
                        + ", Channel: " + channel);

        return SavingsTransferResponse.builder()
                .reference(transferReference)
                .fromTransaction(SavingsTransactionResponse.fromEntity(out))
                .toTransaction(SavingsTransactionResponse.fromEntity(in))
                .build();
    }

    @Transactional
    public SavingsTransactionResponse reverseTransaction(Long transactionId, String reason, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        SavingsTransaction transaction = savingsTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));

        if (transaction.getStatus() != SavingsTransaction.SavingsTransactionStatus.COMPLETED) {
            throw new IllegalStateException("Only COMPLETED transactions can be reversed");
        }

        transaction.setStatus(SavingsTransaction.SavingsTransactionStatus.REVERSED);
        SavingsTransaction saved = savingsTransactionRepository.save(transaction);
        auditService.logEvent(actorId, "SAVINGS_TRANSACTION_REVERSED", "SAVINGS_TRANSACTION", transactionId, reason);
        return SavingsTransactionResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateBalance(Long accountId) {
        List<SavingsTransaction> transactions = savingsTransactionRepository.findByAccountIdAndStatus(
                accountId, SavingsTransaction.SavingsTransactionStatus.COMPLETED);
        BigDecimal balance = BigDecimal.ZERO;

        for (SavingsTransaction tx : transactions) {
            if (isCredit(tx.getTransactionType())) {
                balance = balance.add(creditedAmount(tx));
            } else {
                balance = balance.subtract(tx.getAmount());
            }
        }
        return balance;
    }

    @Transactional(readOnly = true)
    public List<SavingsTransactionResponse> getAccountTransactions(Long accountId) {
        return savingsTransactionRepository.findByAccountId(accountId).stream()
                .map(SavingsTransactionResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SavingsTransactionResponse> getAllTransactions() {
        return savingsTransactionRepository.findAll().stream()
                .map(SavingsTransactionResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SavingsTransactionResponse> getPendingTransactions() {
        return savingsTransactionRepository.findByStatus(SavingsTransaction.SavingsTransactionStatus.PENDING).stream()
                .map(SavingsTransactionResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public SavingsTransactionResponse getTransaction(Long transactionId) {
        return savingsTransactionRepository.findById(transactionId)
                .map(SavingsTransactionResponse::fromEntity)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));
    }

    public BigDecimal getWithdrawalApprovalThreshold() {
        return withdrawalApprovalThreshold;
    }

    private BigDecimal creditedAmount(SavingsTransaction tx) {
        return tx.getNetAmount() == null ? tx.getAmount() : tx.getNetAmount();
    }

    private String normalizeChannel(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private Account findActiveAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new IllegalStateException("Account is not active. Current status: " + account.getStatus());
        }
        return account;
    }

    private void requireApprovalForLargeDebit(SavingsTransaction.SavingsTransactionType type,
            BigDecimal amount, String authorizedBy) {
        boolean needsApproval = (type == SavingsTransaction.SavingsTransactionType.WITHDRAWAL
                || type == SavingsTransaction.SavingsTransactionType.TRANSFER_OUT)
                && amount.compareTo(withdrawalApprovalThreshold) >= 0;
        if (needsApproval && (authorizedBy == null || authorizedBy.isBlank())) {
            throw new IllegalStateException("Supervisor / manager authorization is required for withdrawals of "
                    + withdrawalApprovalThreshold + " and above");
        }
    }

    private boolean isDebit(SavingsTransaction.SavingsTransactionType type) {
        return type == SavingsTransaction.SavingsTransactionType.WITHDRAWAL
                || type == SavingsTransaction.SavingsTransactionType.TRANSFER_OUT
                || type == SavingsTransaction.SavingsTransactionType.FEE;
    }

    private boolean isCredit(SavingsTransaction.SavingsTransactionType type) {
        return type == SavingsTransaction.SavingsTransactionType.DEPOSIT
                || type == SavingsTransaction.SavingsTransactionType.TRANSFER_IN
                || type == SavingsTransaction.SavingsTransactionType.INTEREST;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
