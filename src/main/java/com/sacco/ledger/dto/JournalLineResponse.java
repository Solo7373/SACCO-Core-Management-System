package com.sacco.ledger.dto;

import com.sacco.ledger.entity.JournalLine;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class JournalLineResponse {
    private Long lineId;
    private Long journalId;
    private String accountCode;
    private BigDecimal debit;
    private BigDecimal credit;
    private String description;

    public static JournalLineResponse fromEntity(JournalLine line) {
        return JournalLineResponse.builder()
                .lineId(line.getLineId())
                .journalId(line.getJournalId())
                .accountCode(line.getAccountCode())
                .debit(line.getDebit())
                .credit(line.getCredit())
                .description(line.getDescription())
                .build();
    }
}
