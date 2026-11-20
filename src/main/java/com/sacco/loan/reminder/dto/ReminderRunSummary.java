package com.sacco.loan.reminder.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Aggregate counters for one reminder engine run.
 */
@Data
@Builder
public class ReminderRunSummary {

    private long dispatched;
    private long sent;
    private long failed;
    private long skipped;
    private long suppressed;
    private long providerNotConfigured;
}
