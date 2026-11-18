package com.sacco.storage.drive;

import com.sacco.storage.google.GmailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({GoogleDriveProperties.class, GmailProperties.class})
public class GoogleDriveConfiguration {
}
