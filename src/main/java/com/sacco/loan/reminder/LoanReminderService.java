package com.sacco.loan.reminder;

import com.sacco.audit.AuditService;
import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import com.sacco.communication.AfricasTalkingProperties;
import com.sacco.communication.SmsService;
import com.sacco.communication.WhatsAppService;
import com.sacco.communication.dto.SmsMessageResponse;
import com.sacco.communication.dto.WhatsAppMessageResponse;
import com.sacco.communication.entity.SmsMessage;
import com.sacco.communication.email.EmailNotConfiguredException;
import com.sacco.communication.email.GmailService;
import com.sacco.communication.exception.AfricasTalkingException;
import com.sacco.communication.exception.SmsOptOutException;
import com.sacco.communication.exception.SmsProviderUnavailableException;
import com.sacco.communication.exception.WhatsAppException;
import com.sacco.communication.exception.WhatsAppOptOutException;
import com.sacco.communication.exception.WhatsAppProviderUnavailableException;
import com.sacco.communication.exception.WhatsAppWindowClosedException;
import com.sacco.communication.voice.VoiceCallService;
import com.sacco.loan.entity.Loan;
import com.sacco.loan.entity.LoanReminder;
import com.sacco.loan.entity.LoanSchedule;
import com.sacco.loan.reminder.dto.LoanReminderResponse;
import com.sacco.loan.reminder.dto.ReminderDispatchOutcome;
import com.sacco.loan.reminder.dto.ReminderRunSummary;
import com.sacco.loan.reminder.dto.ReminderSummaryResponse;
import com.sacco.loan.repository.LoanReminderRepository;
import com.sacco.loan.repository.LoanRepository;
import com.sacco.loan.repository.LoanScheduleRepository;
import com.sacco.member.entity.Member;
import com.sacco.member.repository.MemberRepository;
import com.sacco.setup.SystemSettingsService;
import com.sacco.setup.entity.SystemSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Loan due-date reminder engine.
 *
 * <p>The setup wizard stores a reminder policy (days before due, channels,
 * send time, grace period, message template and consent settings). This service
 * turns that policy into concrete dispatches:
 *
 * <ul>
 *   <li>Finds loan instalments whose scheduled send date has arrived.</li>
 *   <li>Ignores paid instalments and members without usable contact details.</li>
 *   <li>Renders the stored template with member and loan context.</li>
 *   <li>Dispatches through SMS, WhatsApp, email or voice using the existing
 *       provider clients, each of which enforces its own policy (opt-outs,
 *       consent, provider configuration).</li>
 *   <li>Records every attempt in {@link LoanReminder} with the idempotency key
 *       (loan, instalment, days-before, channel, send-date).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanReminderService {

    private static final List<Loan.LoanStatus> REMINDER_LOAN_STATUSES =
            List.of(Loan.LoanStatus.DISBURSED, Loan.LoanStatus.IN_ARREARS);

    /** System actor used when the scheduler dispatches a reminder on behalf of the SACCO. */
    private static final Long SYSTEM_ACTOR_ID = 0L;

    private final SystemSettingsService settingsService;
    private final LoanRepository loanRepository;
    private final LoanScheduleRepository loanScheduleRepository;
    private final MemberRepository memberRepository;
    private final LoanReminderRepository loanReminderRepository;
    private final SmsService smsService;
    private final WhatsAppService whatsappService;
    private final GmailService gmailService;
    private final AfricasTalkingProperties atProperties;
    private final VoiceCallService voiceCallService;
    private final AuditService auditService;
    private final PermissionService permissionService;

    // ------------------------------------------------------------------
    // Policy
    // ------------------------------------------------------------------

    public LoanReminderPolicy policy() {
        return LoanReminderPolicy.from(settingsService);
    }

    // ------------------------------------------------------------------
    // Due reminder selection
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<DueReminder> dueReminders(LocalDate today) {
        LocalDate todayDate = today == null ? policy().today() : today;
        List<DueReminder> due = new ArrayList<>();
        for (Loan loan : loanRepository.findByStatusIn(REMINDER_LOAN_STATUSES)) {
            List<LoanSchedule> schedules =
                    loanScheduleRepository.findByLoanIdOrderByInstallmentNoAsc(loan.getLoanId());
            for (LoanSchedule schedule : schedules) {
                if (schedule.getStatus() == LoanSchedule.ScheduleStatus.PAID) {
                    continue;
                }
                BigDecimal remaining = remainingDue(schedule);
                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                if (schedule.getDueDate() == null) {
                    continue;
                }
                List<Integer> days = policy().daysBefore();
                Set<Integer> firedDays = new HashSet<>();
                for (Integer daysBefore : days) {
                    LocalDate sendDate = schedule.getDueDate().minusDays(daysBefore);
                    if (sendDate.isAfter(todayDate) || firedDays.contains(daysBefore)) {
                        continue;
                    }
                    firedDays.add(daysBefore);
                    due.add(toDueReminder(loan, schedule, daysBefore, sendDate, remaining));
                }
                int graceDays = policy().getGraceDays();
                if (graceDays > 0 && !days.contains(graceDays)) {
                    LocalDate overdueSendDate = schedule.getDueDate().plusDays(graceDays);
                    if (!overdueSendDate.isAfter(todayDate)) {
                        due.add(toDueReminder(loan, schedule, graceDays, overdueSendDate, remaining));
                    }
                }
            }
        }
        return due;
    }

    private DueReminder toDueReminder(Loan loan,
                                     LoanSchedule schedule,
                                     int daysBefore,
                                     LocalDate sendDate,
                                     BigDecimal remaining) {
        Member member = memberRepository.findById(loan.getMemberId()).orElse(null);
        String recipient = member == null ? null : member.getPhone();
        String memberName = member == null ? null
                : (member.getFirstName() + " " + member.getLastName()).trim();
        return DueReminder.of(
                loan.getLoanId(),
                schedule.getScheduleId(),
                schedule.getInstallmentNo(),
                schedule.getDueDate(),
                daysBefore,
                sendDate,
                loan.getMemberId(),
                loan.getLoanNumber(),
                remaining,
                policy().getCurrency(),
                recipient,
                memberName,
                member == null ? null : member.getPhone(),
                member == null ? null : member.getEmail());
    }

    // ------------------------------------------------------------------
    // Dispatch
    // ------------------------------------------------------------------

    @Transactional
    public ReminderDispatchOutcome dispatch(DueReminder due, String channel) {
        if (!policy().isChannelEnabled(channel)) {
            return ReminderDispatchOutcome.skipped(null, due.loanId(), due.scheduleId(),
                    due.daysBefore(), channel, "Channel is not enabled in the reminder policy", due.amount(), due.currency());
        }

        LocalDateTime now = policy().now();
        if ("VOICE".equals(channel) && policy().isQuietHours(now)) {
            return ReminderDispatchOutcome.skipped(null, due.loanId(), due.scheduleId(),
                    due.daysBefore(), channel, "Voice quiet hours", due.amount(), due.currency());
        }

        String recipient = due.recipient();
        if (recipient == null || recipient.isBlank()) {
            return ReminderDispatchOutcome.skipped(null, due.loanId(), due.scheduleId(),
                    due.daysBefore(), channel, "No member phone number", due.amount(), due.currency());
        }

        Optional<LoanReminder> existing = loanReminderRepository
                .findByLoanIdAndScheduleIdAndDaysBeforeAndChannelAndSendDate(
                        due.loanId(), due.scheduleId(), due.daysBefore(), channel, due.sendDate());
        if (existing.isPresent()) {
            LoanReminder prior = existing.get();
            if (prior.getStatus() == LoanReminder.ReminderStatus.SENT
                    || prior.getStatus() == LoanReminder.ReminderStatus.SKIPPED
                    || prior.getStatus() == LoanReminder.ReminderStatus.SUPPRESSED
                    || prior.getStatus() == LoanReminder.ReminderStatus.FAILED) {
                return switch (prior.getStatus()) {
                    case SENT -> ReminderDispatchOutcome.suppressed(null, due.loanId(), due.scheduleId(),
                            due.daysBefore(), channel, due.amount(), due.currency());
                    case SKIPPED -> ReminderDispatchOutcome.skipped(prior.getReminderId(), due.loanId(),
                            due.scheduleId(), due.daysBefore(), channel,
                            prior.getFailureReason() == null ? "Reminder already skipped" : prior.getFailureReason(),
                            due.amount(), due.currency());
                    // A failed attempt is terminal for the automatic engine so
                    // the minute-by-minute scheduler cannot hammer the provider.
                    // Staff can retry explicitly from the reminders workspace.
                    case FAILED -> ReminderDispatchOutcome.suppressed(prior.getReminderId(), due.loanId(),
                            due.scheduleId(), due.daysBefore(), channel, due.amount(), due.currency());
                    default -> ReminderDispatchOutcome.suppressed(null, due.loanId(), due.scheduleId(),
                            due.daysBefore(), channel, due.amount(), due.currency());
                };
            }
        }

        LoanReminder reminder = existing.orElseGet(() -> LoanReminder.builder()
                .loanId(due.loanId())
                .scheduleId(due.scheduleId())
                .installmentNo(due.installmentNo())
                .dueDate(due.dueDate())
                .daysBefore(due.daysBefore())
                .sendDate(due.sendDate())
                .channel(channel)
                .recipient(recipient)
                .memberId(due.memberId())
                .loanNumber(due.loanNumber())
                .amount(due.amount())
                .currency(due.currency())
                .renderedMessage(renderFor(due))
                .status(LoanReminder.ReminderStatus.PENDING)
                .build());
        if (existing.isEmpty()) {
            reminder = loanReminderRepository.save(reminder);
        } else {
            resetForRetry(reminder);
            reminder = loanReminderRepository.save(reminder);
        }
        Long reminderId = reminder.getReminderId();

        String rendered = reminder.getRenderedMessage();
        String memberName = due.memberName();
        String loanNumber = due.loanNumber();
        String amountText = due.amount() == null
                ? ""
                : due.amount().setScale(2, RoundingMode.HALF_UP).toPlainString();
        String dueDateText = due.dueDate() == null ? "" : due.dueDate().toString();

        boolean sent = false;
        String failureReason = null;
        String providerMessageId = null;
        LocalDateTime sentAt = LocalDateTime.now();

        try {
            switch (channel) {
                case "SMS" -> {
                    SmsMessageResponse response = smsService.sendImmediate(
                            recipient, rendered, SmsMessage.SmsMessageType.NOTIFICATION,
                            policy().getSmsSenderId(), due.memberId(), SYSTEM_ACTOR_ID);
                    sent = true;
                    providerMessageId = response.providerMessageId();
                }
                case "WHATSAPP" -> {
                    if (policy().getWhatsappTemplateName() != null && !policy().getWhatsappTemplateName().isBlank()) {
                        List<String> values = new ArrayList<>();
                        addValue(values, memberName);
                        addValue(values, loanNumber);
                        addValue(values, amountText);
                        addValue(values, due.currency());
                        addValue(values, dueDateText);
                        addValue(values, policy().getOrganizationName());
                        WhatsAppMessageResponse response = whatsappService.sendTemplateInternal(
                                recipient, policy().getWhatsappTemplateName(),
                                policy().getWhatsappTemplateLanguage(), values, due.memberId(), SYSTEM_ACTOR_ID);
                        sent = true;
                        providerMessageId = response.providerMessageId();
                    } else {
                        WhatsAppMessageResponse response = whatsappService.sendTextInternal(
                                recipient, rendered, null, due.memberId(), SYSTEM_ACTOR_ID);
                        sent = true;
                        providerMessageId = response.providerMessageId();
                    }
                }
                case "EMAIL" -> {
                    String subject = policy().getOrganizationName() == null
                            ? "Loan payment reminder"
                            : policy().getOrganizationName() + " - Loan payment reminder";
                    gmailService.send(due.email(), subject, rendered);
                    sent = true;
                }
                case "VOICE" -> {
                    com.sacco.communication.voice.dto.VoiceCallResponse call =
                            voiceCallService.initiate(due.loanId(), due.memberId(), recipient,
                                    policy().getVoiceCallerId(), rendered, SYSTEM_ACTOR_ID);
                    sent = true;
                    providerMessageId = call.getProviderCallId();
                }
                default -> {
                    failureReason = "Unsupported reminder channel: " + channel;
                }
            }
        } catch (SmsOptOutException | WhatsAppOptOutException | WhatsAppWindowClosedException exception) {
            return recordSkip(reminder, due, channel, exception.getMessage());
        } catch (EmailNotConfiguredException exception) {
            return recordSkip(reminder, due, channel, exception.getMessage());
        } catch (SmsProviderUnavailableException | AfricasTalkingException exception) {
            return recordFailure(reminder, due, channel, exception);
        } catch (WhatsAppProviderUnavailableException exception) {
            return recordFailure(reminder, due, channel, exception);
        } catch (WhatsAppException exception) {
            return recordFailure(reminder, due, channel, exception);
        } catch (RuntimeException exception) {
            return recordFailure(reminder, due, channel, exception);
        }

        if (sent) {
            reminder.setStatus(LoanReminder.ReminderStatus.SENT);
            reminder.setProviderMessageId(providerMessageId);
            reminder.setFailureReason(null);
            reminder.setSentAt(sentAt);
            loanReminderRepository.save(reminder);
            auditService.logEvent(SYSTEM_ACTOR_ID, "LOAN_REMINDER_SENT", "LOAN_REMINDER",
                    reminder.getReminderId(),
                    channel + " reminder sent to loan " + due.loanNumber() + " instalment "
                            + due.daysBefore() + " days before due");
        }

        return sent
                ? ReminderDispatchOutcome.sent(reminderId, due.loanId(), due.scheduleId(), due.daysBefore(),
                        channel, providerMessageId, sentAt, due.amount(), due.currency())
                : ReminderDispatchOutcome.failed(reminderId, due.loanId(), due.scheduleId(), due.daysBefore(),
                        channel, failureReason, due.amount(), due.currency());
    }

    private ReminderDispatchOutcome recordSkip(LoanReminder reminder,
                                               DueReminder due,
                                               String channel,
                                               String reason) {
        String text = truncate(reason == null ? "Reminder skipped" : reason, 500);
        reminder.setStatus(LoanReminder.ReminderStatus.SKIPPED);
        reminder.setFailureReason(text);
        reminder.setProviderMessageId(null);
        reminder.setSentAt(null);
        loanReminderRepository.save(reminder);
        return ReminderDispatchOutcome.skipped(reminder.getReminderId(), due.loanId(),
                due.scheduleId(), due.daysBefore(), channel, text, due.amount(), due.currency());
    }

    private ReminderDispatchOutcome recordFailure(LoanReminder reminder,
                                                  DueReminder due,
                                                  String channel,
                                                  RuntimeException exception) {
        String text = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        text = truncate(text, 500);
        reminder.setStatus(LoanReminder.ReminderStatus.FAILED);
        reminder.setFailureReason(text);
        reminder.setProviderMessageId(null);
        reminder.setSentAt(null);
        loanReminderRepository.save(reminder);
        return ReminderDispatchOutcome.failed(reminder.getReminderId(), due.loanId(),
                due.scheduleId(), due.daysBefore(), channel, text, due.amount(), due.currency());
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void addValue(List<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value);
        }
    }

    private String renderFor(DueReminder due) {
        return policy().render(policy().getTemplate(), due.memberName(), due.loanNumber(),
                due.amount(), due.currency(), due.dueDate(), policy().getOrganizationName());
    }

    private void resetForRetry(LoanReminder reminder) {
        reminder.setStatus(LoanReminder.ReminderStatus.PENDING);
        reminder.setProviderMessageId(null);
        reminder.setFailureReason(null);
        reminder.setSentAt(null);
    }

    private BigDecimal remainingDue(LoanSchedule schedule) {
        return schedule.getTotalDue()
                .subtract(schedule.getPrincipalPaid())
                .subtract(schedule.getInterestPaid())
                .add(schedule.getPenaltyDue())
                .subtract(schedule.getPenaltyPaid());
    }

    private LoanReminder.ReminderStatus parseStatus(String status) {
        try {
            return LoanReminder.ReminderStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown reminder status: " + status);
        }
    }

    // ------------------------------------------------------------------
    // Engine entry point and staff APIs
    // ------------------------------------------------------------------

    @Transactional
    public ReminderRunSummary runDueNow() {
        LocalDate today = policy().today();
        List<DueReminder> due = dueReminders(today);
        ReminderRunSummary summary = ReminderRunSummary.builder().build();
        for (DueReminder reminder : due) {
            for (String channel : policy().channels()) {
                if (!policy().isChannelEnabled(channel)) {
                    continue;
                }
                ReminderDispatchOutcome outcome = dispatch(reminder, channel);
                summary.setDispatched(summary.getDispatched() + 1);
                switch (outcome.getStatus()) {
                    case "SENT" -> summary.setSent(summary.getSent() + 1);
                    case "FAILED" -> summary.setFailed(summary.getFailed() + 1);
                    case "SKIPPED" -> summary.setSkipped(summary.getSkipped() + 1);
                    case "SUPPRESSED" -> summary.setSuppressed(summary.getSuppressed() + 1);
                    default -> {
                    }
                }
            }
        }
        log.info("Reminder run complete: {} dispatched, {} sent, {} failed, {} skipped, {} suppressed",
                summary.getDispatched(), summary.getSent(), summary.getFailed(),
                summary.getSkipped(), summary.getSuppressed());
        return summary;
    }

    /**
     * Manual "run the reminder engine now" action, permission-gated for staff.
     * The scheduler calls {@link #runDueNow()} directly.
     */
    @Transactional
    public ReminderRunSummary runNow(Long actorId) {
        permissionService.require(actorId, Permission.REMINDER_SEND);
        return runDueNow();
    }

    @Transactional(readOnly = true)
    public ReminderSummaryResponse summary(Long actorId) {
        permissionService.require(actorId, Permission.REMINDER_VIEW);
        List<LoanReminder> rows = loanReminderRepository.findAll(PageRequest.of(0, 200)).getContent();
        ReminderSummaryResponse response = ReminderSummaryResponse.builder().build();
        response.setTotal(rows.size());
        for (LoanReminder row : rows) {
            switch (row.getStatus() == null ? "" : row.getStatus().name()) {
                case "SENT" -> response.setSent(response.getSent() + 1);
                case "FAILED" -> response.setFailed(response.getFailed() + 1);
                case "SKIPPED" -> response.setSkipped(response.getSkipped() + 1);
                case "SUPPRESSED" -> response.setSuppressed(response.getSuppressed() + 1);
                default -> {
                }
            }
        }
        return response;
    }

    @Transactional(readOnly = true)
    public Page<LoanReminderResponse> history(int page,
                                               int size,
                                               String channel,
                                               Long loanId,
                                               String status,
                                               Long actorId) {
        permissionService.require(actorId, Permission.REMINDER_VIEW);
        PageRequest pageable = PageRequest.of(
                Math.max(0, page), Math.min(Math.max(1, size), 100));
        LoanReminder.ReminderStatus reminderStatus =
                status == null || status.isBlank() ? null : parseStatus(status);
        String channelFilter = channel == null || channel.isBlank()
                ? null
                : channel.trim().toUpperCase(Locale.ROOT);
        Page<LoanReminder> rows = loanReminderRepository.search(
                reminderStatus, channelFilter, loanId, pageable);
        return rows.map(LoanReminderResponse::from);
    }

    @Transactional(readOnly = true)
    public LoanReminderResponse reminder(Long reminderId, Long actorId) {
        permissionService.require(actorId, Permission.REMINDER_VIEW);
        LoanReminder reminder = loanReminderRepository.findById(reminderId)
                .orElseThrow(() -> new IllegalArgumentException("Loan reminder not found: " + reminderId));
        return LoanReminderResponse.from(reminder);
    }

    @Transactional
    public ReminderDispatchOutcome retry(Long reminderId, Long actorId) {
        permissionService.require(actorId, Permission.REMINDER_MANAGE);
        LoanReminder reminder = loanReminderRepository.findById(reminderId)
                .orElseThrow(() -> new IllegalArgumentException("Loan reminder not found: " + reminderId));
        if (reminder.getStatus() != LoanReminder.ReminderStatus.FAILED) {
            throw new IllegalStateException("Only failed reminders can be retried.");
        }
        Loan loan = loanRepository.findById(reminder.getLoanId())
                .orElseThrow(() -> new IllegalArgumentException("Loan not found: " + reminder.getLoanId()));
        Member member = reminder.getMemberId() == null
                ? null
                : memberRepository.findById(reminder.getMemberId()).orElse(null);
        String memberName = member == null
                ? null
                : (member.getFirstName() + " " + member.getLastName()).trim();
        DueReminder due = DueReminder.of(
                reminder.getLoanId(),
                reminder.getScheduleId(),
                reminder.getInstallmentNo(),
                reminder.getDueDate(),
                reminder.getDaysBefore(),
                reminder.getSendDate(),
                reminder.getMemberId(),
                reminder.getLoanNumber(),
                reminder.getAmount(),
                reminder.getCurrency(),
                reminder.getRecipient(),
                memberName,
                member == null ? null : member.getPhone(),
                member == null ? null : member.getEmail());
        // Clear the previous failed attempt so dispatch treats this as a new
        // try instead of suppressing it as an already-attempted reminder.
        resetForRetry(reminder);
        loanReminderRepository.save(reminder);
        return dispatch(due, reminder.getChannel());
    }
}
