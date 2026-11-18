package com.sacco.storage.google;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Default Gmail API configuration for outbound email. The setup wizard stores
 * the effective values in the database; these properties are the fallback for
 * environment-driven deployments.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "sacco.gmail")
public class GmailProperties {

    private boolean enabled = true;
    private String baseUrl = "https://gmail.googleapis.com";
    private int timeoutMs = 30000;
}
