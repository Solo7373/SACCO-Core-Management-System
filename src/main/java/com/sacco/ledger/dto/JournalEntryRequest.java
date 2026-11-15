package com.sacco.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class JournalEntryRequest {
    private LocalDate entryDate = LocalDate.now();

    @NotBlank(message = "Description is required")
    private String description;

    @NotBlank(message = "Source type is required")
    private String sourceType;

    @NotNull(message = "Source ID is required")
    private Long sourceId;

    @NotEmpty(message = "At least two journal lines are required")
    private List<JournalLineRequest> lines;

    private String currency = "UGX";
}
