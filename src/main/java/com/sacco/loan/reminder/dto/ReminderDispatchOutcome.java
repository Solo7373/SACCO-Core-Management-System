package com.sacco.loan.reminder.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Outcome of one reminder dispatch attempt for one (trigger, channel) pair.
 */
@Data
@Builder
public class ReminderDispatchOutcome {

    private Long reminderId;
    private Long loanId;
    private Long scheduleId;
    private Integer daysBefore;
    private String channel;
    private String status;
    private String providerMessageId;
    private String failureReason;
    private LocalDateTime sentAt;
    private BigDecimal amount;
    private String currency;

    public static ReminderDispatchOutcome sent(Long reminderId,
                                               Long loanId,
                                               Long scheduleId,
                                               Integer daysBefore,
                                               String channel,
                                               String providerMessageId,
                                               LocalDateTime sentAt,
                                               BigDecimal amount,
                                               String currency) {
        return ReminderDispatchOutcome.builder()
                .reminderId(reminderId)
                .loanId(loanId)
                .scheduleId(scheduleId)
                .daysBefore(daysBefore)
                .channel(channel)
                .status("SENT")
                .providerMessageId(providerMessageId)
                .sentAt(sentAt)
                .amount(amount)
                .currency(currency)
                .build();
    }

    public static ReminderDispatchOutcome failed(Long reminderId,
                                                 Long loanId,
                                                 Long scheduleId,
                                                 Integer daysBefore,
                                                 String channel,
                                                 String reason,
                                                 BigDecimal amount,
                                                 String currency) {
        return ReminderDispatchOutcome.builder()
                .reminderId(reminderId)
                .loanId(loanId)
                .scheduleId(scheduleId)
                .daysBefore(daysBefore)
                .channel(channel)
                .status("FAILED")
                .failureReason(reason)
                .amount(amount)
                .currency(currency)
                .build();
    }

    public static ReminderDispatchOutcome skipped(Long reminderId,
                                                  Long loanId,
                                                  Long scheduleId,
                                                  Integer daysBefore,
                                                  String channel,
                                                  String reason,
                                                  BigDecimal amount,
                                                  String currency) {
        return ReminderDispatchOutcome.builder()
                .reminderId(reminderId)
                .loanId(loanId)
                .scheduleId(scheduleId)
                .daysBefore(daysBefore)
                .channel(channel)
                .status("SKIPPED")
                .failureReason(reason)
                .amount(amount)
                .currency(currency)
                .build();
    }

    public static ReminderDispatchOutcome suppressed(Long reminderId,
                                                      Long loanId,
                                                      Long scheduleId,
                                                      Integer daysBefore,
                                                      String channel,
                                                      BigDecimal amount,
                                                      String currency) {
        return ReminderDispatchOutcome.builder()
                .reminderId(reminderId)
                .loanId(loanId)
                .scheduleId(scheduleId)
                .daysBefore(daysBefore)
                .channel(channel)
                .status("SUPPRESSED")
                .amount(amount)
                .currency(currency)
                .build();
    }
}
