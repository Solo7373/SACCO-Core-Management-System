package com.sacco.communication.dto;

import com.sacco.communication.entity.SmsMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SmsSendRequest {

    @NotBlank(message = "recipient is required")
    @Size(max = 20, message = "recipient is too long")
    private String recipient;

    @NotBlank(message = "message is required")
    @Size(max = 1000, message = "message must be 1000 characters or fewer")
    private String message;

    private SmsMessage.SmsMessageType type = SmsMessage.SmsMessageType.NOTIFICATION;

    @Size(max = 20, message = "senderId is too long")
    private String senderId;

    /** Optional link to a member for reporting. */
    private Long memberId;

    /**
     * When true the call waits for the provider. Prefer false for notifications;
     * the outbox dispatcher will send within a few seconds.
     */
    private boolean immediate = false;
}
