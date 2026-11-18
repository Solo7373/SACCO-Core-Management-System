package com.sacco.communication.email.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmailTestRequest {

    /**
     * Optional recipient for a test message. When blank the administrator's
     * account email, or the connected Google account, is used.
     */
    @Size(max = 320)
    private String to;
}
