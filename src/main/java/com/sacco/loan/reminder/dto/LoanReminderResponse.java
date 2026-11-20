package com.sacco.loan.reminder.dto;

import com.sacco.loan.entity.LoanReminder;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Staff-facing reminder history row.
 */
@Data
@Builder
public class LoanReminderResponse {

    private Long reminderId;
    private Long loanId;
    private Long scheduleId;
    private Integer installmentNo;
    private LocalDate dueDate;
    private Integer daysBefore;
    private LocalDate sendDate;
    private String channel;
    private String recipient;
    private Long memberId;
    private String loanNumber;
    private BigDecimal amount;
    private String currency;
    private String renderedMessage;
    private String status;
    private String providerMessageId;
    private String failureReason;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;

    public static LoanReminderResponse from(LoanReminder reminder) {
        return LoanReminderResponse.builder()
                .reminderId(reminder.getReminderId())
                .loanId(reminder.getLoanId())
                .scheduleId(reminder.getScheduleId())
                .installmentNo(reminder.getInstallmentNo())
                .dueDate(reminder.getDueDate())
                .daysBefore(reminder.getDaysBefore())
                .sendDate(reminder.getSendDate())
                .channel(reminder.getChannel())
                .recipient(reminder.getRecipient())
                .memberId(reminder.getMemberId())
                .loanNumber(reminder.getLoanNumber())
                .amount(reminder.getAmount())
                .currency(reminder.getCurrency())
                .renderedMessage(reminder.getRenderedMessage())
                .status(reminder.getStatus() == null ? null : reminder.getStatus().name())
                .providerMessageId(reminder.getProviderMessageId())
                .failureReason(reminder.getFailureReason())
                .sentAt(reminder.getSentAt())
                .createdAt(reminder.getCreatedAt())
                .build();
    }
}
