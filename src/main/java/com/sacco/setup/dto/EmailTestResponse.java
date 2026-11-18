package com.sacco.setup.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EmailTestResponse {
    private boolean connected;
    private String message;
    private String emailAddress;
    private long messagesTotal;
    private LocalDateTime testedAt;
    private String lastError;
}
