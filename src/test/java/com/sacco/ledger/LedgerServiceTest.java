package com.sacco.ledger;

import com.sacco.audit.AuditService;
import com.sacco.ledger.dto.JournalEntryRequest;
import com.sacco.ledger.dto.JournalEntryResponse;
import com.sacco.ledger.dto.JournalLineRequest;
import com.sacco.ledger.entity.ChartOfAccounts;
import com.sacco.ledger.entity.JournalEntry;
import com.sacco.ledger.entity.JournalLine;
import com.sacco.ledger.repository.ChartOfAccountsRepository;
import com.sacco.ledger.repository.JournalEntryRepository;
import com.sacco.ledger.repository.JournalLineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LedgerServiceTest {

    @Mock private JournalEntryRepository journalEntryRepository;
    @Mock private JournalLineRepository journalLineRepository;
    @Mock private ChartOfAccountsRepository chartOfAccountsRepository;
    @Mock private AuditService auditService;

    @InjectMocks private LedgerService ledgerService;

    private JournalEntryRequest balancedEntry;
    private JournalEntry savedEntry;

    @BeforeEach
    void setUp() {
        JournalLineRequest line1 = new JournalLineRequest();
        line1.setAccountCode("1000");
        line1.setDebit(new BigDecimal("10000"));
        line1.setCredit(BigDecimal.ZERO);

        JournalLineRequest line2 = new JournalLineRequest();
        line2.setAccountCode("1100");
        line2.setDebit(BigDecimal.ZERO);
        line2.setCredit(new BigDecimal("10000"));

        balancedEntry = new JournalEntryRequest();
        balancedEntry.setDescription("Test deposit");
        balancedEntry.setSourceType("SAVINGS_DEPOSIT");
        balancedEntry.setSourceId(1L);
        balancedEntry.setLines(List.of(line1, line2));

        savedEntry = JournalEntry.builder()
                .journalId(1L)
                .status(JournalEntry.JournalEntryStatus.POSTED)
                .totalDebit(new BigDecimal("10000"))
                .totalCredit(new BigDecimal("10000"))
                .build();

        when(chartOfAccountsRepository.existsById(any())).thenReturn(true);
    }

    @Test
    void createAndPostEntry_shouldPostBalancedEntry() {
        when(journalEntryRepository.save(any(JournalEntry.class))).thenReturn(savedEntry);
        when(journalLineRepository.save(any())).thenReturn(JournalLine.builder().lineId(1L).journalId(1L).accountCode("1000").debit(new BigDecimal("10000")).credit(BigDecimal.ZERO).build());

        JournalEntryResponse response = ledgerService.createAndPostEntry(balancedEntry, 1L);

        assertThat(response.getStatus()).isEqualTo(JournalEntry.JournalEntryStatus.POSTED);
        assertThat(response.getTotalDebit()).isEqualTo(new BigDecimal("10000"));
        assertThat(response.getTotalCredit()).isEqualTo(new BigDecimal("10000"));
        verify(journalEntryRepository).save(any(JournalEntry.class));
    }

    @Test
    void createAndPostEntry_shouldRejectUnbalancedEntry() {
        JournalLineRequest line1 = new JournalLineRequest();
        line1.setAccountCode("1000");
        line1.setDebit(new BigDecimal("10000"));
        line1.setCredit(BigDecimal.ZERO);

        JournalLineRequest line2 = new JournalLineRequest();
        line2.setAccountCode("1100");
        line2.setDebit(BigDecimal.ZERO);
        line2.setCredit(new BigDecimal("5000"));

        JournalEntryRequest unbalanced = new JournalEntryRequest();
        unbalanced.setDescription("Unbalanced");
        unbalanced.setSourceType("TEST");
        unbalanced.setSourceId(1L);
        unbalanced.setLines(List.of(line1, line2));

        assertThatThrownBy(() -> ledgerService.createAndPostEntry(unbalanced, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must balance");
    }

    @Test
    void createAndPostEntry_shouldRejectZeroAmountEntry() {
        JournalLineRequest line1 = new JournalLineRequest();
        line1.setAccountCode("1000");
        line1.setDebit(BigDecimal.ZERO);
        line1.setCredit(BigDecimal.ZERO);

        JournalLineRequest line2 = new JournalLineRequest();
        line2.setAccountCode("1100");
        line2.setDebit(BigDecimal.ZERO);
        line2.setCredit(BigDecimal.ZERO);

        JournalEntryRequest zeroEntry = new JournalEntryRequest();
        zeroEntry.setDescription("Zero");
        zeroEntry.setSourceType("TEST");
        zeroEntry.setSourceId(1L);
        zeroEntry.setLines(List.of(line1, line2));

        assertThatThrownBy(() -> ledgerService.createAndPostEntry(zeroEntry, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must have either a debit or credit amount");
    }

    @Test
    void createAndPostEntry_shouldRejectInvalidAccountCode() {
        when(chartOfAccountsRepository.existsById("9999")).thenReturn(false);

        JournalLineRequest line1 = new JournalLineRequest();
        line1.setAccountCode("9999");
        line1.setDebit(new BigDecimal("100"));
        line1.setCredit(BigDecimal.ZERO);

        JournalLineRequest line2 = new JournalLineRequest();
        line2.setAccountCode("1100");
        line2.setDebit(BigDecimal.ZERO);
        line2.setCredit(new BigDecimal("100"));

        JournalEntryRequest invalidAccount = new JournalEntryRequest();
        invalidAccount.setDescription("Invalid account");
        invalidAccount.setSourceType("TEST");
        invalidAccount.setSourceId(1L);
        invalidAccount.setLines(List.of(line1, line2));

        assertThatThrownBy(() -> ledgerService.createAndPostEntry(invalidAccount, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account code not found");
    }

    @Test
    void reverseEntry_shouldSetStatusToReversed() {
        when(journalEntryRepository.findById(1L)).thenReturn(Optional.of(savedEntry));
        when(journalEntryRepository.save(any(JournalEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        JournalEntryResponse response = ledgerService.reverseEntry(1L, "Correction", 1L);

        assertThat(response.getStatus()).isEqualTo(JournalEntry.JournalEntryStatus.REVERSED);
    }

    @Test
    void reverseEntry_shouldRejectAlreadyReversed() {
        savedEntry.setStatus(JournalEntry.JournalEntryStatus.REVERSED);
        when(journalEntryRepository.findById(1L)).thenReturn(Optional.of(savedEntry));

        assertThatThrownBy(() -> ledgerService.reverseEntry(1L, "Double reverse", 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already reversed");
    }

    @Test
    void getAccountBalance_shouldReturnZeroForNoTransactions() {
        when(journalLineRepository.findByAccountCode("1000")).thenReturn(List.of());

        BigDecimal balance = ledgerService.getAccountBalance("1000");

        assertThat(balance).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void createAndPostEntry_shouldRejectSingleLine() {
        JournalLineRequest line1 = new JournalLineRequest();
        line1.setAccountCode("1000");
        line1.setDebit(new BigDecimal("10000"));
        line1.setCredit(BigDecimal.ZERO);

        JournalEntryRequest singleLine = new JournalEntryRequest();
        singleLine.setDescription("Single line");
        singleLine.setSourceType("TEST");
        singleLine.setSourceId(1L);
        singleLine.setLines(List.of(line1));

        assertThatThrownBy(() -> ledgerService.createAndPostEntry(singleLine, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least two lines");
    }
}
