package com.sacco.setup.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DriveTestResponse {
    private boolean connected;
    private String message;
    private String folderName;
    private String account;
}
