package com.sacco.setup.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class MessagingSetupRequest {

    @Size(max = 50)
    private String smsSenderId;

    @Size(max = 200)
    private String whatsappDisplayName;

    @Size(max = 50)
    private String whatsappPhoneNumber;

    @Size(max = 200)
    private String emailFromName;

    @Size(max = 200)
    private String emailFromAddress;

    @Size(max = 200)
    private String emailReplyTo;

    @Size(max = 50)
    private String voiceCallerId;

    /** e.g. 5,3,1,0 for five days before, three days before, one day before and on the due date. */
    private List<Integer> reminderDaysBefore;

    /** SMS, WHATSAPP, EMAIL and/or VOICE. */
    private List<String> reminderChannels;

    @Size(max = 10)
    private String reminderSendTime;

    @Min(0)
    @Max(90)
    private Integer reminderGraceDays;

    @Size(max = 2000)
    private String reminderTemplate;

    /** Approved Meta WhatsApp utility template used for automated loan reminders, for example "loan_due_reminder". */
    @Size(max = 200)
    private String reminderWhatsappTemplate;

    /** Language code of the approved WhatsApp reminder template, for example "en". */
    @Size(max = 20)
    private String reminderWhatsappLanguage;

    /** Master switch for Africa Talking voice calls (mirrors AT_VOICE_ENABLED). */
    private Boolean voiceEnabled;

    /** Voice quiet hours start, 24-hour HH:mm, used to suppress calls. */
    @Size(max = 5)
    private String voiceQuietHoursStart;

    /** Voice quiet hours end, 24-hour HH:mm, used to suppress calls. */
    @Size(max = 5)
    private String voiceQuietHoursEnd;

    private Boolean consentRequired;

    @Size(max = 500)
    private String optOutText;
}
