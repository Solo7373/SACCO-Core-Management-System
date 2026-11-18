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

    private Boolean consentRequired;

    @Size(max = 500)
    private String optOutText;
}
