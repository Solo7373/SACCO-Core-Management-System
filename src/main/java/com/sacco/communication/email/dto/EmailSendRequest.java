package com.sacco.communication.email.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmailSendRequest {

    @NotBlank(message = "Recipient email address is required")
    @Size(max = 320)
    private String to;

    @NotBlank(message = "Email subject is required")
    @Size(max = 200)
    private String subject;

    @NotBlank(message = "Email message body is required")
    @Size(max = 100000)
    private String body;
}
