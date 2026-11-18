package com.sacco.setup.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StorageSetupRequest {

    /** GOOGLE_DRIVE or LOCAL. */
    private String provider;

    /** SERVICE_ACCOUNT, OAUTH or MOCK. */
    private String authMode;

    @Size(max = 255)
    private String folderId;

    @Size(max = 255)
    private String googleAccountEmail;

    @Size(max = 500)
    private String gmailBaseUrl;

    @Size(max = 500)
    private String baseUrl;

    @Size(max = 500)
    private String tokenUri;

    @Size(max = 200)
    private String applicationName;

    /** GOOGLE_DRIVE, LOCAL, QUEUE_ONLY, DIRECTORY or HTTP. */
    private String backupMode;

    /** When true, the background scheduler runs complete snapshots automatically. */
    private Boolean automaticBackups;

    @Min(1)
    @Max(3650)
    private Integer retentionDays;

    @Size(max = 50)
    private String schedule;

    /** Optional OAuth credentials; they are written to the protected secrets directory. */
    private String oauthClientId;
    private String oauthClientSecret;
    private String oauthRefreshToken;
}
