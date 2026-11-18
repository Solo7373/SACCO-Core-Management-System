package com.sacco.storage.drive;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Default Google Drive configuration. The first-run setup wizard stores the
 * effective values in the database; these properties are the fallback for
 * environment-driven deployments.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "sacco.google-drive")
public class GoogleDriveProperties {

    private boolean enabled = false;
    private String authMode = "SERVICE_ACCOUNT";
    private String baseUrl = "https://www.googleapis.com";
    private String tokenUri = "https://oauth2.googleapis.com/token";
    private String folderId = "";
    private String applicationName = "SCMS";

    /** Optional path to a service-account JSON file outside the repository. */
    private String credentialsFile = "";

    /**
     * Directory used for credentials uploaded through the setup wizard.
     * Defaults to {@code ~/.sacco/secrets} when blank.
     */
    private String secretsDir = "";

    private int timeoutMs = 30000;
}
