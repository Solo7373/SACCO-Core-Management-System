package com.sacco.loan.reminder;

import com.sacco.loan.reminder.LoanReminderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background loan due-date reminder engine.
 *
 * <p>Ticks every minute by default and dispatches every instalment trigger
 * whose send date has arrived. The engine is idempotent per
 * (loan, instalment, days-before, channel, send-date) so a restart during a
 * run cannot duplicate a reminder, and it catches up the send date that was
 * missed while the application was down.
 */
@Slf4j
@Component
public class LoanReminderScheduler {

    private final LoanReminderService loanReminderService;

    public LoanReminderScheduler(LoanReminderService loanReminderService) {
        this.loanReminderService = loanReminderService;
    }

    @Scheduled(cron = "${sacco.reminders.scheduler.cron:0 * * * * *}")
    public void runDueReminders() {
        try {
            // The engine reads the stored reminder policy on every tick, so a
            // settings change applies without a restart and the tick interval
            // gives catch-up behaviour after downtime.
            loanReminderService.runDueNow();
        } catch (Exception exception) {
            log.error("Loan reminder engine failed on schedule", exception);
        }
    }
}
