package com.sacco.ledger.repository;

import com.sacco.ledger.entity.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
    List<JournalEntry> findByStatus(JournalEntry.JournalEntryStatus status);
    List<JournalEntry> findBySourceTypeAndSourceId(String sourceType, Long sourceId);
    List<JournalEntry> findByEntryDateBetween(LocalDate start, LocalDate end);
}
