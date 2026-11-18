package com.sacco.communication.email.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmailStatusResponse {
    private boolean configured;
    private boolean enabled;
    private String authMode;
    private String fromAddress;
    private String fromName;
    private String replyTo;
    private String message;
}
