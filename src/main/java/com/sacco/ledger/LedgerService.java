package com.sacco.ledger;

import com.sacco.audit.AuditService;
import com.sacco.ledger.dto.*;
import com.sacco.ledger.entity.ChartOfAccounts;
import com.sacco.ledger.entity.JournalEntry;
import com.sacco.ledger.entity.JournalLine;
import com.sacco.ledger.repository.ChartOfAccountsRepository;
import com.sacco.ledger.repository.JournalEntryRepository;
import com.sacco.ledger.repository.JournalLineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

    private final JournalEntryRepository journalEntryRepository;
    private final JournalLineRepository journalLineRepository;
    private final ChartOfAccountsRepository chartOfAccountsRepository;
    private final AuditService auditService;

    @Transactional
    public JournalEntryResponse createAndPostEntry(JournalEntryRequest request, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        validateLines(request.getLines());
        BigDecimal totalDebit = request.getLines().stream()
                .map(JournalLineRequest::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = request.getLines().stream()
                .map(JournalLineRequest::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalStateException("Journal entry must balance. Total debit: " + totalDebit + ", Total credit: " + totalCredit);
        }
        if (totalDebit.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalStateException("Journal entry amount cannot be zero");
        }

        JournalEntry entry = JournalEntry.builder()
                .entryDate(request.getEntryDate())
                .description(request.getDescription())
                .sourceType(request.getSourceType())
                .sourceId(request.getSourceId())
                .status(JournalEntry.JournalEntryStatus.POSTED)
                .totalDebit(totalDebit)
                .totalCredit(totalCredit)
                .currency(request.getCurrency())
                .postedBy(actorId)
                .postedAt(LocalDateTime.now())
                .build();

        JournalEntry saved = journalEntryRepository.save(entry);

        List<JournalLine> savedLines = new ArrayList<>();
        for (JournalLineRequest lineReq : request.getLines()) {
            JournalLine line = JournalLine.builder()
                    .journalEntry(saved)
                    .journalId(saved.getJournalId())
                    .accountCode(lineReq.getAccountCode())
                    .debit(lineReq.getDebit() != null ? lineReq.getDebit() : BigDecimal.ZERO)
                    .credit(lineReq.getCredit() != null ? lineReq.getCredit() : BigDecimal.ZERO)
                    .description(lineReq.getDescription())
                    .build();
            savedLines.add(journalLineRepository.save(line));
        }
        saved.setLines(savedLines);

        auditService.logEvent(actorId, "JOURNAL_POSTED", "JOURNAL_ENTRY", saved.getJournalId(), 
                "Type: " + request.getSourceType() + ", Amount: " + totalDebit);
        return JournalEntryResponse.fromEntity(saved);
    }

    @Transactional
    public JournalEntryResponse reverseEntry(Long journalId, String reason, Long actorId) {
        actorId = com.sacco.auth.SecurityUtils.currentUserId() != null ? com.sacco.auth.SecurityUtils.currentUserId() : actorId;
        JournalEntry entry = journalEntryRepository.findById(journalId)
                .orElseThrow(() -> new IllegalArgumentException("Journal entry not found: " + journalId));
        
        if (entry.getStatus() == JournalEntry.JournalEntryStatus.REVERSED) {
            throw new IllegalStateException("Entry is already reversed");
        }
        
        entry.setStatus(JournalEntry.JournalEntryStatus.REVERSED);
        entry.setReversedBy(actorId);
        entry.setReversedAt(LocalDateTime.now());
        entry.setReversalReason(reason);
        
        JournalEntry saved = journalEntryRepository.save(entry);
        auditService.logEvent(actorId, "JOURNAL_REVERSED", "JOURNAL_ENTRY", journalId, reason);
        return JournalEntryResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public BigDecimal getAccountBalance(String accountCode) {
        List<JournalLine> lines = journalLineRepository.findByAccountCode(accountCode);
        BigDecimal balance = BigDecimal.ZERO;
        for (JournalLine line : lines) {
            if (line.getJournalEntry().getStatus() == JournalEntry.JournalEntryStatus.POSTED) {
                balance = balance.add(line.getDebit()).subtract(line.getCredit());
            }
        }
        return balance;
    }

    @Transactional(readOnly = true)
    public List<TrialBalanceResponse> generateTrialBalance(LocalDate asOfDate) {
        List<ChartOfAccounts> accounts = chartOfAccountsRepository.findAll();
        List<TrialBalanceResponse> trialBalance = new ArrayList<>();
        
        for (ChartOfAccounts account : accounts) {
            List<JournalLine> lines = journalLineRepository.findByAccountCode(account.getAccountCode());
            BigDecimal totalDebit = BigDecimal.ZERO;
            BigDecimal totalCredit = BigDecimal.ZERO;
            
            for (JournalLine line : lines) {
                JournalEntry entry = line.getJournalEntry();
                if (entry.getStatus() == JournalEntry.JournalEntryStatus.POSTED && !entry.getEntryDate().isAfter(asOfDate)) {
                    totalDebit = totalDebit.add(line.getDebit());
                    totalCredit = totalCredit.add(line.getCredit());
                }
            }
            
            if (totalDebit.compareTo(BigDecimal.ZERO) > 0 || totalCredit.compareTo(BigDecimal.ZERO) > 0) {
                trialBalance.add(TrialBalanceResponse.builder()
                        .accountCode(account.getAccountCode())
                        .accountName(account.getName())
                        .category(account.getCategory().name())
                        .totalDebit(totalDebit)
                        .totalCredit(totalCredit)
                        .netBalance(totalDebit.subtract(totalCredit))
                        .generatedAt(asOfDate)
                        .build());
            }
        }
        return trialBalance;
    }

    @Transactional(readOnly = true)
    public JournalEntryResponse getEntry(Long journalId) {
        JournalEntry entry = journalEntryRepository.findById(journalId)
                .orElseThrow(() -> new IllegalArgumentException("Journal entry not found: " + journalId));
        return JournalEntryResponse.fromEntity(entry);
    }

    @Transactional(readOnly = true)
    public List<JournalEntryResponse> getEntriesBySource(String sourceType, Long sourceId) {
        return journalEntryRepository.findBySourceTypeAndSourceId(sourceType, sourceId).stream()
                .map(JournalEntryResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChartOfAccounts> getChartOfAccounts() {
        return chartOfAccountsRepository.findAll();
    }

    private void validateLines(List<JournalLineRequest> lines) {
        if (lines == null || lines.size() < 2) {
            throw new IllegalStateException("A journal entry must have at least two lines");
        }
        for (JournalLineRequest line : lines) {
            if (line.getDebit().compareTo(BigDecimal.ZERO) > 0 && line.getCredit().compareTo(BigDecimal.ZERO) > 0) {
                throw new IllegalStateException("A journal line cannot have both debit and credit. Account: " + line.getAccountCode());
            }
            if (line.getDebit().compareTo(BigDecimal.ZERO) == 0 && line.getCredit().compareTo(BigDecimal.ZERO) == 0) {
                throw new IllegalStateException("A journal line must have either a debit or credit amount. Account: " + line.getAccountCode());
            }
            if (!chartOfAccountsRepository.existsById(line.getAccountCode())) {
                throw new IllegalArgumentException("Account code not found: " + line.getAccountCode());
            }
        }
    }
}