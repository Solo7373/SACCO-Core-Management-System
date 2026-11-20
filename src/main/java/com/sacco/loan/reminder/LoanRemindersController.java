package com.sacco.loan.reminder;

import com.sacco.loan.reminder.dto.LoanReminderResponse;
import com.sacco.loan.reminder.dto.ReminderDispatchOutcome;
import com.sacco.loan.reminder.dto.ReminderRunSummary;
import com.sacco.loan.reminder.dto.ReminderSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff loan due-date reminder workspace: counters, history and the manual
 * "run the engine now" action.
 *
 * <p>The background scheduler runs the same engine on the configured cron
 * ({@code sacco.reminders.scheduler.cron}, every minute by default). Every
 * dispatch is idempotent per (loan, instalment, days-before, channel,
 * send-date) and written to the reminder outbox before it leaves the process.
 */
@RestController
@RequestMapping("/api/loans/reminders")
@RequiredArgsConstructor
public class LoanRemindersController {

    private final LoanReminderService loanReminderService;

    @GetMapping("/summary")
    public ResponseEntity<ReminderSummaryResponse> summary(
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(loanReminderService.summary(actorId));
    }

    @GetMapping
    public ResponseEntity<Page<LoanReminderResponse>> reminders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Long loanId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(loanReminderService.history(
                page, size, channel, loanId, status, actorId));
    }

    @GetMapping("/{reminderId}")
    public ResponseEntity<LoanReminderResponse> reminder(
            @PathVariable Long reminderId,
            @RequestParam(defaultValue = "1") Long actorId) {
        try {
            return ResponseEntity.ok(loanReminderService.reminder(reminderId, actorId));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/run")
    public ResponseEntity<ReminderRunSummary> run(
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(loanReminderService.runNow(actorId));
    }

    @PostMapping("/{reminderId}/retry")
    public ResponseEntity<ReminderDispatchOutcome> retry(
            @PathVariable Long reminderId,
            @RequestParam(defaultValue = "1") Long actorId) {
        return ResponseEntity.ok(loanReminderService.retry(reminderId, actorId));
    }
}
