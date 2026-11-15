package com.sacco.account;

import com.sacco.account.dto.AccountRequest;
import com.sacco.account.dto.AccountResponse;
import com.sacco.account.entity.Account;
import com.sacco.account.entity.AccountHolder;
import com.sacco.account.repository.AccountHolderRepository;
import com.sacco.account.repository.AccountRepository;
import com.sacco.audit.AuditService;
import com.sacco.common.NumberSequenceService;
import com.sacco.member.entity.Member;
import com.sacco.member.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private AccountHolderRepository accountHolderRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private NumberSequenceService numberSequenceService;
    @Mock private AuditService auditService;

    @InjectMocks private AccountService accountService;

    private AccountRequest savingsRequest;
    private Account savedAccount;
    private Member activeMember;

    @BeforeEach
    void setUp() {
        savingsRequest = new AccountRequest();
        savingsRequest.setAccountType(Account.AccountType.SAVINGS);
        savingsRequest.setCurrency("UGX");

        activeMember = Member.builder()
                .memberId(1L)
                .membershipNo("MEM-2026-0001")
                .firstName("John")
                .lastName("Doe")
                .status(Member.MemberStatus.ACTIVE)
                .build();

        savedAccount = Account.builder()
                .accountId(1L)
                .accountNumber("SAV-2026-0001")
                .accountType(Account.AccountType.SAVINGS)
                .currency("UGX")
                .status(Account.AccountStatus.PENDING)
                .build();
    }

    @Test
    void openAccount_shouldCreatePendingAccountWithPrimaryHolder() {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(activeMember));
        when(numberSequenceService.formatted(anyString(), anyString())).thenReturn("SAV-2026-0001");
        when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);
        when(accountHolderRepository.save(any(AccountHolder.class))).thenReturn(new AccountHolder());

        AccountResponse response = accountService.openAccount(savingsRequest, 1L, 1L);

        assertThat(response.getAccountNumber()).startsWith("SAV-");
        assertThat(response.getStatus()).isEqualTo(Account.AccountStatus.PENDING);
        verify(accountRepository).save(any(Account.class));
        verify(accountHolderRepository).save(any(AccountHolder.class));
        verify(numberSequenceService).formatted("SAVINGS", "SAV");
    }

    @Test
    void openAccount_shouldRejectInactiveMember() {
        activeMember.setStatus(Member.MemberStatus.PENDING);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(activeMember));

        assertThatThrownBy(() -> accountService.openAccount(savingsRequest, 1L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active members");
    }

    @Test
    void openAccount_shouldRequireFixedDepositTerms() {
        savingsRequest.setAccountType(Account.AccountType.FIXED_DEPOSIT);
        when(memberRepository.findById(1L)).thenReturn(Optional.of(activeMember));

        assertThatThrownBy(() -> accountService.openAccount(savingsRequest, 1L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("term is required");
    }

    @Test
    void activateAccount_shouldChangeStatusToActive() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(savedAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = accountService.activateAccount(1L, 1L);

        assertThat(response.getStatus()).isEqualTo(Account.AccountStatus.ACTIVE);
    }

    @Test
    void activateAccount_shouldRejectNonPendingAccount() {
        savedAccount.setStatus(Account.AccountStatus.ACTIVE);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(savedAccount));

        assertThatThrownBy(() -> accountService.activateAccount(1L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING");
    }

    @Test
    void freezeAccount_shouldSetStatusToFrozen() {
        savedAccount.setStatus(Account.AccountStatus.ACTIVE);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(savedAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = accountService.freezeAccount(1L, "Suspicious activity", 1L);

        assertThat(response.getStatus()).isEqualTo(Account.AccountStatus.FROZEN);
    }

    @Test
    void closeAccount_shouldSetStatusToClosed() {
        savedAccount.setStatus(Account.AccountStatus.ACTIVE);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(savedAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = accountService.closeAccount(1L, "Member exit", 1L);

        assertThat(response.getStatus()).isEqualTo(Account.AccountStatus.CLOSED);
    }

    @Test
    void markDormant_shouldSetStatusToDormant() {
        savedAccount.setStatus(Account.AccountStatus.ACTIVE);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(savedAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountResponse response = accountService.markDormant(1L, 1L);

        assertThat(response.getStatus()).isEqualTo(Account.AccountStatus.DORMANT);
        assertThat(response.getDormantSince()).isNotNull();
    }

    @Test
    void runDormancyCheck_shouldMarkStaleActiveAccounts() {
        Account stale = Account.builder()
                .accountId(2L)
                .accountNumber("SAV-2026-0002")
                .accountType(Account.AccountType.SAVINGS)
                .currency("UGX")
                .status(Account.AccountStatus.ACTIVE)
                .lastActivityAt(LocalDateTime.now().minusMonths(8))
                .build();
        when(accountRepository.findByStatus(Account.AccountStatus.ACTIVE)).thenReturn(List.of(stale));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        int marked = accountService.runDormancyCheck(1L);

        assertThat(marked).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo(Account.AccountStatus.DORMANT);
    }

    @Test
    void recordActivity_shouldReactivateDormantAccount() {
        savedAccount.setStatus(Account.AccountStatus.DORMANT);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(savedAccount));
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        accountService.recordActivity(1L, LocalDateTime.now());

        assertThat(savedAccount.getStatus()).isEqualTo(Account.AccountStatus.ACTIVE);
        assertThat(savedAccount.getDormantSince()).isNull();
    }

    @Test
    void addAccountHolder_shouldRejectWhenAccountClosed() {
        savedAccount.setStatus(Account.AccountStatus.CLOSED);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(savedAccount));

        var request = new com.sacco.account.dto.AccountHolderRequest();
        request.setMemberId(2L);
        request.setHolderType(AccountHolder.HolderType.JOINT);

        assertThatThrownBy(() -> accountService.addAccountHolder(1L, request, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void getAccountsByMember_shouldReturnEmptyListWhenNoAccounts() {
        when(accountHolderRepository.findByMemberId(99L)).thenReturn(List.of());

        List<AccountResponse> result = accountService.getAccountsByMember(99L);

        assertThat(result).isEmpty();
    }
}
