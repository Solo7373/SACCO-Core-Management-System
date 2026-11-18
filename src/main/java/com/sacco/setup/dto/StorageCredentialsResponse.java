package com.sacco.setup.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StorageCredentialsResponse {
    private boolean stored;
    private String fileName;
    private String message;
}
