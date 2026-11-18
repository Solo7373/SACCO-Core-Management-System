package com.sacco.setup.dto;

import com.sacco.backup.dto.SetupBackupSummary;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SetupStatusResponse {

    private boolean setupRequired;
    private boolean completed;
    private LocalDateTime completedAt;
    private boolean adminConfigured;
    private boolean adminUsesDefaultPassword;
    private String adminUsername;
    private String adminEmail;
    /**
     * Issued only by the administrator step so the wizard can continue with an
     * authenticated session. It is null on every ordinary status response.
     */
    private String token;
    private Long userId;
    private boolean readyToComplete;
    private List<String> missingFields;
    private Organization organization;
    private Storage storage;
    private Messaging messaging;
    private Billing billing;

    @Data
    @Builder
    public static class Organization {
        private String legalName;
        private String tradingName;
        private String registrationNumber;
        private String taxPin;
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String country;
        private String phone;
        private String email;
        private String website;
        private String timezone;
        private String currency;
    }

    @Data
    @Builder
    public static class Storage {
        private String provider;
        private String authMode;
        private String folderId;
        private String googleAccountEmail;
        private String gmailBaseUrl;
        private String baseUrl;
        private String applicationName;
        private boolean credentialsConfigured;
        private String credentialsFileName;
        private boolean connected;
        private LocalDateTime lastTestedAt;
        private String lastError;
        private boolean emailConnected;
        private LocalDateTime emailLastTestedAt;
        private String emailLastError;
        private String backupMode;
        private String effectiveBackupMode;
        private boolean automaticBackupsEnabled;
        private Integer retentionDays;
        private String schedule;
        private SetupBackupSummary backup;
    }

    @Data
    @Builder
    public static class Messaging {
        private String smsSenderId;
        private String whatsappDisplayName;
        private String whatsappPhoneNumber;
        private String emailFromName;
        private String emailFromAddress;
        private String emailReplyTo;
        private String voiceCallerId;
        private List<Integer> reminderDaysBefore;
        private List<String> reminderChannels;
        private String reminderSendTime;
        private Integer reminderGraceDays;
        private String reminderTemplate;
        private Boolean consentRequired;
        private String optOutText;
    }

    @Data
    @Builder
    public static class Billing {
        private String provider;
        private String accountName;
        private String accountNumber;
        private String currency;
        private String contactEmail;
        private String chargeBearer;
        private BigDecimal smsUnitCost;
        private BigDecimal whatsappUnitCost;
        private BigDecimal emailUnitCost;
        private BigDecimal voiceUnitCost;
        private BigDecimal lowBalanceThreshold;
        private BigDecimal prepaidBalance;
    }
}
