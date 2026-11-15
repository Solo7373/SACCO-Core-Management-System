package com.sacco.account;

import com.sacco.account.dto.*;
import com.sacco.account.entity.Account;
import com.sacco.account.entity.AccountHolder;
import com.sacco.account.repository.AccountHolderRepository;
import com.sacco.account.repository.AccountRepository;
import com.sacco.audit.AuditService;
import com.sacco.common.NumberSequenceService;
import com.sacco.member.entity.Member;
import com.sacco.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountHolderRepository accountHolderRepository;
    private final MemberRepository memberRepository;
    private final NumberSequenceService numberSequenceService;
    private final AuditService auditService;

    private static final int DORMANT_AFTER_MONTHS = 6;

    @Transactional
    public AccountResponse openAccount(AccountRequest request, Long primaryMemberId, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        Member member = memberRepository.findById(primaryMemberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found: " + primaryMemberId));

        if (member.getStatus() != Member.MemberStatus.ACTIVE) {
            throw new IllegalStateException("Only active members can open an account");
        }

        validateFixedDeposit(request);

        String prefix = switch (request.getAccountType()) {
            case SAVINGS -> "SAV";
            case SHARE_CAPITAL -> "SHR";
            case LOAN_DISBURSEMENT -> "LN";
            case FIXED_DEPOSIT -> "FD";
        };
        String accountNumber = numberSequenceService.formatted(request.getAccountType().name(), prefix);

        Account account = Account.builder()
                .accountNumber(accountNumber)
                .accountType(request.getAccountType())
                .currency(request.getCurrency() == null ? "UGX" : request.getCurrency())
                .status(Account.AccountStatus.PENDING)
                .branchId(request.getBranchId())
                .lastActivityAt(LocalDateTime.now())
                .fixedDepositTermMonths(request.getAccountType() == Account.AccountType.FIXED_DEPOSIT
                        ? request.getFixedDepositTermMonths() : null)
                .fixedDepositInterestRate(request.getAccountType() == Account.AccountType.FIXED_DEPOSIT
                        ? request.getFixedDepositInterestRate() : null)
                .fixedDepositMaturityDate(request.getAccountType() == Account.AccountType.FIXED_DEPOSIT
                        ? LocalDate.now().plusMonths(request.getFixedDepositTermMonths()) : null)
                .build();

        Account saved = accountRepository.save(account);

        AccountHolder holder = AccountHolder.builder()
                .accountId(saved.getAccountId())
                .memberId(primaryMemberId)
                .holderType(AccountHolder.HolderType.PRIMARY)
                .effectiveFrom(LocalDate.now())
                .build();
        accountHolderRepository.save(holder);

        auditService.logEvent(actorId, "ACCOUNT_OPENED", "ACCOUNT", saved.getAccountId(),
                "Type: " + request.getAccountType() + ", Number: " + saved.getAccountNumber());
        return toResponse(saved);
    }

    @Transactional
    public AccountResponse activateAccount(Long accountId, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        Account account = findAccount(accountId);
        if (account.getStatus() != Account.AccountStatus.PENDING) {
            throw new IllegalStateException("Only PENDING accounts can be activated");
        }
        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setLastActivityAt(LocalDateTime.now());
        account.setDormantSince(null);
        Account saved = accountRepository.save(account);
        auditService.logEvent(actorId, "ACCOUNT_ACTIVATED", "ACCOUNT", accountId, null);
        return toResponse(saved);
    }

    @Transactional
    public AccountResponse freezeAccount(Long accountId, String reason, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        Account account = findAccount(accountId);
        if (account.getStatus() == Account.AccountStatus.CLOSED) {
            throw new IllegalStateException("Closed accounts cannot be frozen");
        }
        account.setStatus(Account.AccountStatus.FROZEN);
        Account saved = accountRepository.save(account);
        auditService.logEvent(actorId, "ACCOUNT_FROZEN", "ACCOUNT", accountId, reason);
        return toResponse(saved);
    }

    @Transactional
    public AccountResponse unfreezeAccount(Long accountId, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        Account account = findAccount(accountId);
        if (account.getStatus() != Account.AccountStatus.FROZEN
                && account.getStatus() != Account.AccountStatus.DORMANT) {
            throw new IllegalStateException("Only FROZEN or DORMANT accounts can be reactivated");
        }
        account.setStatus(Account.AccountStatus.ACTIVE);
        account.setLastActivityAt(LocalDateTime.now());
        account.setDormantSince(null);
        Account saved = accountRepository.save(account);
        auditService.logEvent(actorId, "ACCOUNT_UNFROZEN", "ACCOUNT", accountId, null);
        return toResponse(saved);
    }

    @Transactional
    public AccountResponse closeAccount(Long accountId, String reason, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        Account account = findAccount(accountId);
        if (account.getStatus() == Account.AccountStatus.CLOSED) {
            throw new IllegalStateException("Account is already closed");
        }
        account.setStatus(Account.AccountStatus.CLOSED);
        account.setClosedAt(LocalDateTime.now());
        Account saved = accountRepository.save(account);
        auditService.logEvent(actorId, "ACCOUNT_CLOSED", "ACCOUNT", accountId, reason);
        return toResponse(saved);
    }

    @Transactional
    public AccountResponse markDormant(Long accountId, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        Account account = findAccount(accountId);
        if (account.getStatus() != Account.AccountStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE accounts can be marked dormant");
        }
        account.setStatus(Account.AccountStatus.DORMANT);
        account.setDormantSince(LocalDateTime.now());
        Account saved = accountRepository.save(account);
        auditService.logEvent(actorId, "ACCOUNT_MARKED_DORMANT", "ACCOUNT", accountId,
                "No activity for at least " + DORMANT_AFTER_MONTHS + " months");
        return toResponse(saved);
    }

    @Transactional
    public int runDormancyCheck(Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(DORMANT_AFTER_MONTHS);
        int marked = 0;
        for (Account account : accountRepository.findByStatus(Account.AccountStatus.ACTIVE)) {
            LocalDateTime lastActivity = account.getLastActivityAt();
            if (lastActivity == null) {
                lastActivity = account.getOpenedAt() != null ? account.getOpenedAt() : account.getCreatedAt();
            }
            if (lastActivity != null && lastActivity.isBefore(cutoff)) {
                account.setStatus(Account.AccountStatus.DORMANT);
                account.setDormantSince(LocalDateTime.now());
                accountRepository.save(account);
                marked++;
            }
        }
        if (marked > 0) {
            auditService.logEvent(actorId, "ACCOUNT_DORMANCY_CHECK", "ACCOUNT", 0L,
                    "Marked " + marked + " account(s) dormant");
        }
        return marked;
    }

    @Transactional
    public void recordActivity(Long accountId, LocalDateTime activityAt) {
        accountRepository.findById(accountId).ifPresent(account -> {
            account.setLastActivityAt(activityAt);
            if (account.getStatus() == Account.AccountStatus.DORMANT) {
                account.setStatus(Account.AccountStatus.ACTIVE);
                account.setDormantSince(null);
            }
            accountRepository.save(account);
        });
    }

    @Transactional
    public AccountHolderResponse addAccountHolder(Long accountId, AccountHolderRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        Account account = findAccount(accountId);
        if (account.getStatus() == Account.AccountStatus.CLOSED) {
            throw new IllegalStateException("Cannot add holder to a closed account");
        }
        if (!memberRepository.existsById(request.getMemberId())) {
            throw new IllegalArgumentException("Member not found: " + request.getMemberId());
        }

        AccountHolder holder = AccountHolder.builder()
                .accountId(accountId)
                .memberId(request.getMemberId())
                .holderType(request.getHolderType())
                .effectiveFrom(request.getEffectiveFrom() != null ? request.getEffectiveFrom() : LocalDate.now())
                .approvalReference(request.getApprovalReference())
                .build();
        AccountHolder saved = accountHolderRepository.save(holder);
        auditService.logEvent(actorId, "ACCOUNT_HOLDER_ADDED", "ACCOUNT", accountId,
                "Member: " + request.getMemberId());
        return AccountHolderResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<AccountHolderResponse> getAccountHolders(Long accountId) {
        return accountHolderRepository.findByAccountId(accountId).stream()
                .map(AccountHolderResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long accountId) {
        return toResponse(findAccount(accountId));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByMember(Long memberId) {
        List<Long> accountIds = accountHolderRepository.findByMemberId(memberId).stream()
                .map(AccountHolder::getAccountId)
                .toList();
        if (accountIds.isEmpty()) {
            return List.of();
        }
        return accountRepository.findAllById(accountIds).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> getAccountsByStatus(Account.AccountStatus status) {
        return accountRepository.findByStatus(status).stream()
                .map(this::toResponse)
                .toList();
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
    }

    private AccountResponse toResponse(Account account) {
        Long primaryMemberId = accountHolderRepository.findByAccountId(account.getAccountId()).stream()
                .filter(holder -> holder.getHolderType() == AccountHolder.HolderType.PRIMARY)
                .map(AccountHolder::getMemberId)
                .findFirst()
                .orElse(null);
        Member member = primaryMemberId == null ? null : memberRepository.findById(primaryMemberId).orElse(null);
        return AccountResponse.fromEntity(account, member);
    }

    private void validateFixedDeposit(AccountRequest request) {
        if (request.getAccountType() != Account.AccountType.FIXED_DEPOSIT) {
            return;
        }
        if (request.getFixedDepositTermMonths() == null || request.getFixedDepositTermMonths() < 1) {
            throw new IllegalStateException("Fixed deposit term is required and must be at least one month");
        }
        if (request.getFixedDepositInterestRate() == null) {
            throw new IllegalStateException("Fixed deposit interest rate is required");
        }
    }
}
