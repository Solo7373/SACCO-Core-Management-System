package com.sacco.setup;

import com.sacco.setup.entity.SystemSettings;
import com.sacco.setup.repository.SystemSettingsRepository;
import com.sacco.storage.drive.GoogleDriveCredentialsService;
import com.sacco.storage.drive.GoogleDriveProperties;
import com.sacco.storage.drive.GoogleDriveSettings;
import com.sacco.storage.google.GmailProperties;
import com.sacco.storage.google.GmailSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Reads the single go-live configuration row and derives effective settings
 * for the backup and storage layers.
 */
@Service
@RequiredArgsConstructor
public class SystemSettingsService {

    public static final String DEFAULT_REMINDER_TEMPLATE =
            "Dear {memberName}, your loan {loanNumber} instalment of {currency} {amount} "
                    + "is due on {dueDate}. Please pay on time to avoid penalties. "
                    + "{organisationName}";

    private final SystemSettingsRepository repository;
    private final GoogleDriveProperties driveProperties;
    private final GmailProperties gmailProperties;
    private final GoogleDriveCredentialsService credentialsService;

    @Value("${sacco.backup.cloud.mode:QUEUE_ONLY}")
    private String environmentBackupMode;

    @Transactional
    public SystemSettings getOrCreate() {
        return repository.findFirstByOrderBySettingsIdAsc()
                .orElseGet(() -> repository.save(defaultSettings()));
    }

    @Transactional
    public SystemSettings save(SystemSettings settings) {
        return repository.save(settings);
    }

    @Transactional(readOnly = true)
    public SystemSettings getExisting() {
        return repository.findFirstByOrderBySettingsIdAsc().orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean isSetupCompleted() {
        return repository.findFirstByOrderBySettingsIdAsc()
                .map(settings -> Boolean.TRUE.equals(settings.getSetupCompleted()))
                .orElse(false);
    }

    public GoogleDriveSettings driveSettings() {
        SystemSettings settings = getExisting();
        String authMode = settings == null || isBlank(settings.getDriveAuthMode())
                ? driveProperties.getAuthMode()
                : settings.getDriveAuthMode();
        String baseUrl = settings == null || isBlank(settings.getDriveBaseUrl())
                ? driveProperties.getBaseUrl()
                : settings.getDriveBaseUrl();
        String tokenUri = settings == null || isBlank(settings.getDriveTokenUri())
                ? driveProperties.getTokenUri()
                : settings.getDriveTokenUri();
        String credentialsPath = settings == null || isBlank(settings.getDriveCredentialsPath())
                ? driveProperties.getCredentialsFile()
                : settings.getDriveCredentialsPath();
        String folderId = settings == null || isBlank(settings.getDriveFolderId())
                ? driveProperties.getFolderId()
                : settings.getDriveFolderId();
        String applicationName = settings == null || isBlank(settings.getDriveApplicationName())
                ? driveProperties.getApplicationName()
                : settings.getDriveApplicationName();
        return new GoogleDriveSettings(
                authMode,
                baseUrl,
                tokenUri,
                credentialsPath,
                folderId,
                applicationName,
                driveProperties.getTimeoutMs());
    }

    public boolean isDriveConfigured() {
        SystemSettings settings = getExisting();
        if (settings == null || !"GOOGLE_DRIVE".equalsIgnoreCase(settings.getStorageProvider())) {
            return false;
        }
        if (isBlank(settings.getDriveFolderId())) {
            return false;
        }
        if ("MOCK".equalsIgnoreCase(settings.getDriveAuthMode())) {
            return true;
        }
        return credentialsService.exists(settings.getDriveCredentialsPath());
    }

    /**
     * Automatic backups are on by default, matching the WhatsApp model where
     * the device copies its data in the background without user action.
     */
    public boolean isAutomaticBackupEnabled() {
        SystemSettings settings = getExisting();
        return settings == null || !Boolean.FALSE.equals(settings.getBackupEnabled());
    }

    public String backupSchedule() {
        SystemSettings settings = getExisting();
        return settings == null || isBlank(settings.getBackupSchedule())
                ? "HOURLY"
                : settings.getBackupSchedule().trim().toUpperCase(Locale.ROOT);
    }

    public int backupRetentionDays() {
        SystemSettings settings = getExisting();
        if (settings == null || settings.getBackupRetentionDays() == null) {
            return 30;
        }
        return Math.max(settings.getBackupRetentionDays(), 1);
    }

    /**
     * Effective Gmail settings. The OAuth refresh token is the same one used
     * for Drive backups, so the backup account is also the email sender.
     */
    public GmailSettings gmailSettings() {
        SystemSettings settings = getExisting();
        String authMode = settings == null || isBlank(settings.getDriveAuthMode())
                ? driveProperties.getAuthMode()
                : settings.getDriveAuthMode();
        String baseUrl = settings == null || isBlank(settings.getGmailBaseUrl())
                ? gmailProperties.getBaseUrl()
                : settings.getGmailBaseUrl();
        String tokenUri = settings == null || isBlank(settings.getDriveTokenUri())
                ? driveProperties.getTokenUri()
                : settings.getDriveTokenUri();
        String credentialsPath = settings == null || isBlank(settings.getDriveCredentialsPath())
                ? driveProperties.getCredentialsFile()
                : settings.getDriveCredentialsPath();
        String fromAddress = settings == null
                ? null
                : firstNonBlank(settings.getGoogleAccountEmail(), settings.getEmailFromAddress());
        String fromName = settings == null
                ? null
                : firstNonBlank(settings.getEmailFromName(), settings.getLegalName());
        String replyTo = settings == null ? null : settings.getEmailReplyTo();
        return new GmailSettings(
                authMode,
                baseUrl,
                tokenUri,
                credentialsPath,
                fromAddress,
                fromName,
                replyTo,
                gmailProperties.getTimeoutMs());
    }

    /**
     * Email is usable once the Gmail profile has been verified and the shared
     * OAuth credentials are present. Mock mode supports local testing without
     * Google credentials.
     */
    public boolean isEmailConfigured() {
        SystemSettings settings = getExisting();
        if (settings == null || !Boolean.TRUE.equals(settings.getEmailConnected())) {
            return false;
        }
        if (isBlank(settings.getGoogleAccountEmail())
                && isBlank(settings.getEmailFromAddress())) {
            return false;
        }
        if ("MOCK".equalsIgnoreCase(settings.getDriveAuthMode())) {
            return true;
        }
        return "OAUTH".equalsIgnoreCase(settings.getDriveAuthMode())
                && credentialsService.exists(settings.getDriveCredentialsPath());
    }

    public String effectiveEmailFromAddress() {
        SystemSettings settings = getExisting();
        return settings == null
                ? null
                : firstNonBlank(settings.getGoogleAccountEmail(), settings.getEmailFromAddress());
    }

    /**
     * The setup wizard's storage choice wins once setup is complete. Before
     * that, keep the existing environment-driven behaviour so the desktop app
     * can still start and show the wizard.
     */
    public String effectiveBackupMode() {
        SystemSettings settings = getExisting();
        if (settings != null && Boolean.TRUE.equals(settings.getSetupCompleted())) {
            if ("GOOGLE_DRIVE".equalsIgnoreCase(settings.getStorageProvider())) {
                return "GOOGLE_DRIVE";
            }
            if (!isBlank(settings.getBackupMode())) {
                return settings.getBackupMode().trim().toUpperCase(Locale.ROOT);
            }
        }
        return environmentBackupMode == null || environmentBackupMode.isBlank()
                ? "QUEUE_ONLY"
                : environmentBackupMode.trim().toUpperCase(Locale.ROOT);
    }

    private SystemSettings defaultSettings() {
        return SystemSettings.builder()
                .timezone("Africa/Kampala")
                .country("Uganda")
                .currency("UGX")
                .storageProvider("GOOGLE_DRIVE")
                .driveAuthMode("SERVICE_ACCOUNT")
                .driveBaseUrl("https://www.googleapis.com")
                .driveTokenUri("https://oauth2.googleapis.com/token")
                .driveApplicationName("SCMS")
                .gmailBaseUrl("https://gmail.googleapis.com")
                .emailConnected(false)
                .backupMode("GOOGLE_DRIVE")
                .backupEnabled(true)
                .backupRetentionDays(30)
                .backupSchedule("HOURLY")
                .reminderDaysBefore("5,3,1,0")
                .reminderChannels("SMS,WHATSAPP")
                .reminderSendTime("08:00")
                .reminderGraceDays(3)
                .reminderTemplate(DEFAULT_REMINDER_TEMPLATE)
                .consentRequired(true)
                .optOutText("Reply STOP to opt out of SMS reminders.")
                .billingCurrency("UGX")
                .chargeBearer("SACCO")
                .smsUnitCost(java.math.BigDecimal.ZERO)
                .whatsappUnitCost(java.math.BigDecimal.ZERO)
                .emailUnitCost(java.math.BigDecimal.ZERO)
                .voiceUnitCost(java.math.BigDecimal.ZERO)
                .lowBalanceThreshold(java.math.BigDecimal.ZERO)
                .prepaidBalance(java.math.BigDecimal.ZERO)
                .setupCompleted(false)
                .build();
    }

    /**
     * Name printed on statements, demand letters and official exports. The
     * trading name is preferred because it is the name members recognise.
     */
    public String organizationName() {
        SystemSettings settings = getExisting();
        if (settings != null) {
            if (!isBlank(settings.getTradingName())) {
                return settings.getTradingName().trim();
            }
            if (!isBlank(settings.getLegalName())) {
                return settings.getLegalName().trim();
            }
        }
        return "SACCO Core Management System";
    }

    /** Phone, email and website shown in document headers. */
    public String organizationContactLine() {
        SystemSettings settings = getExisting();
        if (settings == null) {
            return "";
        }
        return joinNonBlank(" | ",
                settings.getPhone(),
                settings.getEmail(),
                settings.getWebsite());
    }

    /** Postal/physical address shown in document headers. */
    public String organizationAddressLine() {
        SystemSettings settings = getExisting();
        if (settings == null) {
            return "";
        }
        return joinNonBlank(", ",
                settings.getAddressLine1(),
                settings.getAddressLine2(),
                settings.getCity(),
                settings.getCountry());
    }

    private String joinNonBlank(String separator, String... values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (isBlank(value)) {
                continue;
            }
            if (result.length() > 0) {
                result.append(separator);
            }
            result.append(value.trim());
        }
        return result.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String firstNonBlank(String first, String second) {
        if (!isBlank(first)) {
            return first.trim();
        }
        return isBlank(second) ? null : second.trim();
    }
}
