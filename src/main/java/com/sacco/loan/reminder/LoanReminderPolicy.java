package com.sacco.loan.reminder;

import com.sacco.setup.SystemSettingsService;
import com.sacco.setup.entity.SystemSettings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import lombok.Getter;

/**
 * Immutable view of the loan reminder policy stored in the single
 * {@link SystemSettings} row. The scheduler, the staff APIs and the tests all
 * read through here so there is exactly one place that interprets the stored
 * reminder configuration.
 */
@Getter
public class LoanReminderPolicy {

    public static final List<String> SUPPORTED_CHANNELS =
            List.of("SMS", "WHATSAPP", "EMAIL", "VOICE");

    private final String timezone;
    private final ZoneId zone;
    private final String currency;
    private final List<Integer> daysBefore;
    private final List<String> channels;
    private final String sendTime;
    private final int graceDays;
    private final String template;
    private final boolean consentRequired;
    private final String optOutText;
    private final String organizationName;
    private final String smsSenderId;
    private final String whatsappDisplayName;
    private final String whatsappPhoneNumber;
    private final String emailFromName;
    private final String emailFromAddress;
    private final String emailReplyTo;
    private final String voiceCallerId;
    private final String whatsappTemplateName;
    private final String whatsappTemplateLanguage;
    private final int voiceQuietHoursStart;
    private final int voiceQuietHoursEnd;

    private LoanReminderPolicy(SystemSettings settings, String organizationName) {
        this.timezone = settings == null || isBlank(settings.getTimezone())
                ? "Africa/Kampala"
                : settings.getTimezone().trim();
        this.zone = ZoneId.of(this.timezone);
        this.currency = settings == null || isBlank(settings.getCurrency())
                ? "UGX"
                : settings.getCurrency().trim();
        this.daysBefore = parseDays(settings == null ? null : settings.getReminderDaysBefore());
        this.channels = parseChannels(settings == null ? null : settings.getReminderChannels());
        this.sendTime = settings == null || isBlank(settings.getReminderSendTime())
                ? "08:00"
                : settings.getReminderSendTime().trim();
        this.graceDays = settings == null || settings.getReminderGraceDays() == null
                ? 3
                : settings.getReminderGraceDays();
        this.template = settings == null || isBlank(settings.getReminderTemplate())
                ? SystemSettingsService.DEFAULT_REMINDER_TEMPLATE
                : settings.getReminderTemplate();
        this.consentRequired = settings == null || settings.getConsentRequired() == null
                ? Boolean.TRUE
                : settings.getConsentRequired();
        this.optOutText = settings == null ? null : settings.getOptOutText();
        this.organizationName = organizationName;
        this.smsSenderId = settings == null ? null : settings.getSmsSenderId();
        this.whatsappDisplayName = settings == null ? null : settings.getWhatsappDisplayName();
        this.whatsappPhoneNumber = settings == null ? null : settings.getWhatsappPhoneNumber();
        this.emailFromName = settings == null ? null : settings.getEmailFromName();
        this.emailFromAddress = settings == null ? null : settings.getEmailFromAddress();
        this.emailReplyTo = settings == null ? null : settings.getEmailReplyTo();
        this.voiceCallerId = settings == null ? null : settings.getVoiceCallerId();
        this.whatsappTemplateName = settings == null ? null : settings.getReminderWhatsappTemplate();
        this.whatsappTemplateLanguage = settings == null ? null : settings.getReminderWhatsappLanguage();
        this.voiceQuietHoursStart = parseClock(
                settings == null ? null : settings.getVoiceQuietHoursStart(), 8, 0);
        this.voiceQuietHoursEnd = parseClock(
                settings == null ? null : settings.getVoiceQuietHoursEnd(), 18, 0);
    }

    public static LoanReminderPolicy from(SystemSettings settings, String organizationName) {
        return new LoanReminderPolicy(settings, organizationName);
    }

    public static LoanReminderPolicy from(SystemSettingsService settingsService) {
        return from(settingsService.getExisting(), settingsService.organizationName());
    }

    public ZoneId zone() {
        return this.zone;
    }

    public LocalDateTime now() {
        return LocalDateTime.now(this.zone);
    }

    public LocalDate today() {
        return LocalDate.now(this.zone);
    }

    public List<Integer> daysBefore() {
        return new ArrayList<>(this.daysBefore);
    }

    public List<String> channels() {
        return new ArrayList<>(this.channels);
    }

    public boolean isChannelEnabled(String channel) {
        return this.channels.contains(channel);
    }

    public boolean isVoiceAvailable() {
        return this.voiceCallerId != null && !this.voiceCallerId.isBlank();
    }

    /** True when the current local time is inside the configured quiet hours. */
    public boolean isQuietHours(LocalDateTime now) {
        int minuteOfDay = now.getHour() * 60 + now.getMinute();
        int start = this.voiceQuietHoursStart * 60;
        int end = this.voiceQuietHoursEnd * 60;
        return minuteOfDay >= start && minuteOfDay < end;
    }

    public String render(String template, String memberName, String loanNumber,
                        BigDecimal amount, String currency, LocalDate dueDate, String organisationName) {
        if (isBlank(template)) {
            return null;
        }
        String message = template.trim();
        message = message.replace("{memberName}", memberName == null ? "" : memberName);
        message = message.replace("{loanNumber}", loanNumber == null ? "" : loanNumber);
        message = message.replace("{amount}",
                amount == null ? "" : amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
        message = message.replace("{currency}", currency == null ? "" : currency);
        message = message.replace("{dueDate}", dueDate == null ? "" : dueDate.toString());
        message = message.replace("{organisationName}", organisationName == null ? "" : organisationName);
        return message.trim();
    }

    public BigDecimal amountDue(com.sacco.loan.entity.LoanSchedule schedule) {
        return schedule.getTotalDue()
                .subtract(schedule.getPrincipalPaid())
                .subtract(schedule.getInterestPaid())
                .add(schedule.getPenaltyDue())
                .subtract(schedule.getPenaltyPaid());
    }

    private static List<Integer> parseDays(String raw) {
        List<Integer> days = new ArrayList<>();
        if (isBlank(raw)) {
            return days;
        }
        try {
            Arrays.stream(raw.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .mapToInt(Integer::parseInt)
                    .filter(value -> value >= 0 && value <= 90)
                    .distinct()
                    .forEach(days::add);
        } catch (NumberFormatException ignored) {
            // An unparsable policy is treated as "no reminders"; the setup UI
            // rejects such values before they are stored.
        }
        return days;
    }

    private static List<String> parseChannels(String raw) {
        List<String> channels = new ArrayList<>();
        if (isBlank(raw)) {
            return channels;
        }
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .filter(value -> SUPPORTED_CHANNELS.contains(value))
                .distinct()
                .forEach(channels::add);
        return channels;
    }

    private static int parseClock(String value, int fallbackHour, int fallbackMinute) {
        if (isBlank(value)) {
            return fallbackHour * 60 + fallbackMinute;
        }
        try {
            String[] parts = value.trim().split(":");
            int hour = Integer.parseInt(parts[0]);
            int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return hour * 60 + minute;
        } catch (NumberFormatException exception) {
            return fallbackHour * 60 + fallbackMinute;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
