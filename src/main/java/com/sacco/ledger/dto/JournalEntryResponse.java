package com.sacco.ledger.dto;

import com.sacco.ledger.entity.JournalEntry;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class JournalEntryResponse {
    private Long journalId;
    private LocalDate entryDate;
    private String description;
    private String sourceType;
    private Long sourceId;
    private JournalEntry.JournalEntryStatus status;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private String currency;
    private Long postedBy;
    private LocalDateTime postedAt;
    private String reversalReason;
    private LocalDateTime createdAt;
    private List<JournalLineResponse> lines;

    public static JournalEntryResponse fromEntity(JournalEntry entry) {
        JournalEntryResponse response = JournalEntryResponse.builder()
                .journalId(entry.getJournalId())
                .entryDate(entry.getEntryDate())
                .description(entry.getDescription())
                .sourceType(entry.getSourceType())
                .sourceId(entry.getSourceId())
                .status(entry.getStatus())
                .totalDebit(entry.getTotalDebit())
                .totalCredit(entry.getTotalCredit())
                .currency(entry.getCurrency())
                .postedBy(entry.getPostedBy())
                .postedAt(entry.getPostedAt())
                .reversalReason(entry.getReversalReason())
                .createdAt(entry.getCreatedAt())
                .build();
        if (entry.getLines() != null) {
            response.setLines(entry.getLines().stream().map(JournalLineResponse::fromEntity).collect(Collectors.toList()));
        }
        return response;
    }
}
