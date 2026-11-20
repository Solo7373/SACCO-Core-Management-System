package com.sacco.loan.reminder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single reminder trigger that is due for dispatch. One row per
 * (loan, instalment, days-before-due, send-date) and per candidate channel.
 */
public record DueReminder(
        Long loanId,
        Long scheduleId,
        Integer installmentNo,
        LocalDate dueDate,
        Integer daysBefore,
        LocalDate sendDate,
        Long memberId,
        String loanNumber,
        BigDecimal amount,
        String currency,
        String recipient,
        String memberName,
        String phone,
        String email) {

    public static DueReminder of(Long loanId,
                                 Long scheduleId,
                                 Integer installmentNo,
                                 LocalDate dueDate,
                                 Integer daysBefore,
                                 LocalDate sendDate,
                                 Long memberId,
                                 String loanNumber,
                                 BigDecimal amount,
                                 String currency,
                                 String recipient,
                                 String memberName,
                                 String phone,
                                 String email) {
        return new DueReminder(loanId, scheduleId, installmentNo, dueDate, daysBefore, sendDate,
                memberId, loanNumber, amount, currency, recipient, memberName, phone, email);
    }
}
