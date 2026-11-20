package com.sacco.loan.reminder.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Staff-facing aggregate reminder counters for the Reminders workspace.
 */
@Data
@Builder
public class ReminderSummaryResponse {

    private long total;
    private long sent;
    private long failed;
    private long skipped;
    private long suppressed;
}
