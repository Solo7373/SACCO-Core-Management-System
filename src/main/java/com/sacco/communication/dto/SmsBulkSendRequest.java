package com.sacco.communication.dto;

import com.sacco.communication.entity.SmsMessage;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SmsBulkSendRequest {

    @NotEmpty(message = "recipients are required")
    @Size(max = 500, message = "at most 500 recipients are allowed per request")
    private List<@NotBlank @Size(max = 20) String> recipients = new ArrayList<>();

    @NotBlank(message = "message is required")
    @Size(max = 1000, message = "message must be 1000 characters or fewer")
    private String message;

    private SmsMessage.SmsMessageType type = SmsMessage.SmsMessageType.NOTIFICATION;

    @Size(max = 20, message = "senderId is too long")
    private String senderId;

    private boolean immediate = false;
}
