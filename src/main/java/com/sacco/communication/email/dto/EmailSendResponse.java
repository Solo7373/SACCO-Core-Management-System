package com.sacco.communication.email.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EmailSendResponse {
    private String messageId;
    private String threadId;
    private String to;
    private String from;
    private String subject;
    private LocalDateTime sentAt;
}
