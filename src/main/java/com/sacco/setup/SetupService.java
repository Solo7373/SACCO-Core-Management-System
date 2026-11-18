package com.sacco.setup;

import com.sacco.audit.AuditService;
import com.sacco.auth.AccessDeniedException;
import com.sacco.auth.JwtService;
import com.sacco.auth.Permission;
import com.sacco.auth.PermissionService;
import com.sacco.auth.SecurityUtils;
import com.sacco.auth.entity.User;
import com.sacco.auth.repository.UserRepository;
import com.sacco.backup.BackupService;
import com.sacco.backup.dto.BackupStatusResponse;
import com.sacco.communication.email.GmailConnectionResult;
import com.sacco.communication.email.GmailSendResult;
import com.sacco.communication.email.GmailService;
import com.sacco.communication.email.dto.EmailSendResponse;
import com.sacco.setup.dto.AdminSetupRequest;
import com.sacco.setup.dto.BillingSetupRequest;
import com.sacco.setup.dto.DriveTestResponse;
import com.sacco.setup.dto.EmailTestResponse;
import com.sacco.setup.dto.MessagingSetupRequest;
import com.sacco.setup.dto.OrganizationSetupRequest;
import com.sacco.setup.dto.SetupStatusResponse;
import com.sacco.setup.dto.StorageCredentialsResponse;
import com.sacco.setup.dto.StorageSetupRequest;
import com.sacco.setup.entity.SystemSettings;
import com.sacco.storage.drive.DriveConnectionResult;
import com.sacco.storage.drive.GoogleDriveCredentialsService;
import com.sacco.storage.drive.GoogleDriveService;
import com.sacco.storage.google.GoogleOAuthTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Guided go-live setup. Before setup is complete the wizard is reachable
 * without a login only while no administrator exists, so a new installation
 * can create its first administrator. Every mutation after that requires the
 * SETTINGS_MANAGE permission, including while setup is still incomplete.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SetupService {

    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final Set<String> CHANNELS = Set.of("SMS", "WHATSAPP", "EMAIL", "VOICE");
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";

    private final SystemSettingsService settingsService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PermissionService permissionService;
    private final AuditService auditService;
    private final GoogleDriveService googleDriveService;
    private final GoogleDriveCredentialsService credentialsService;
    private final GmailService gmailService;
    private final GoogleOAuthTokenService googleOAuthTokenService;
    private final BackupService backupService;
    private final JwtService jwtService;

    @Transactional
    public SetupStatusResponse status() {
        SystemSettings settings = settingsService.getOrCreate();
        User admin = primaryAdmin();

        // Once an administrator exists, the configuration contains sender
        // identities and billing details that must not be readable anonymously.
        if (admin != null && !canManageSettings()) {
            return SetupStatusResponse.builder()
                    .setupRequired(!Boolean.TRUE.equals(settings.getSetupCompleted()))
                    .completed(Boolean.TRUE.equals(settings.getSetupCompleted()))
                    .adminConfigured(true)
                    .readyToComplete(false)
                    .missingFields(List.of())
                    .build();
        }
        return buildStatus(settings, admin);
    }

    private SetupStatusResponse buildStatus(SystemSettings settings, User admin) {
        List<String> missing = missingFields(settings, admin);

        return SetupStatusResponse.builder()
                .setupRequired(!Boolean.TRUE.equals(settings.getSetupCompleted()))
                .completed(Boolean.TRUE.equals(settings.getSetupCompleted()))
                .completedAt(settings.getSetupCompletedAt())
                .adminConfigured(admin != null)
                .adminUsesDefaultPassword(isDefaultAdminPassword(admin))
                .adminUsername(admin == null ? null : admin.getUsername())
                .adminEmail(admin == null ? null : admin.getEmail())
                .userId(admin == null ? null : admin.getUserId())
                .readyToComplete(missing.isEmpty())
                .missingFields(missing)
                .organization(organization(settings))
                .storage(storage(settings))
                .messaging(messaging(settings))
                .billing(billing(settings))
                .build();
    }

    @Transactional
    public SetupStatusResponse saveOrganization(OrganizationSetupRequest request) {
        requireAccess();
        SystemSettings settings = settingsService.getOrCreate();
        settings.setLegalName(trim(request.getLegalName()));
        settings.setTradingName(trim(request.getTradingName()));
        settings.setRegistrationNumber(trim(request.getRegistrationNumber()));
        settings.setTaxPin(trim(request.getTaxPin()));
        settings.setAddressLine1(trim(request.getAddressLine1()));
        settings.setAddressLine2(trim(request.getAddressLine2()));
        settings.setCity(trim(request.getCity()));
        settings.setCountry(trim(request.getCountry()));
        settings.setPhone(trim(request.getPhone()));
        settings.setEmail(trim(request.getEmail()));
        settings.setWebsite(trim(request.getWebsite()));
        settings.setTimezone(defaultIfBlank(request.getTimezone(), "Africa/Kampala"));
        settings.setCurrency(defaultIfBlank(request.getCurrency(), "UGX").toUpperCase(Locale.ROOT));
        settingsService.save(settings);

        audit("SETUP_ORGANIZATION_SAVED", "Organisation profile updated during setup");
        return status();
    }

    @Transactional
    public SetupStatusResponse saveAdmin(AdminSetupRequest request) {
        requireAccess();
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new IllegalStateException("The administrator passwords do not match.");
        }
        if (request.getNewPassword().length() < 8) {
            throw new IllegalStateException("The administrator password must be at least 8 characters.");
        }
        if (DEFAULT_ADMIN_PASSWORD.equals(request.getNewPassword())) {
            throw new IllegalStateException("Choose a password other than the old factory default.");
        }

        User admin = primaryAdmin();
        if (admin == null) {
            if (userRepository.existsByUsername(request.getUsername().trim())) {
                throw new IllegalStateException("That username is already in use.");
            }
            admin = User.builder()
                    .username(request.getUsername().trim().toLowerCase(Locale.ROOT))
                    .passwordHash(passwordEncoder.encode(request.getNewPassword()))
                    .email(trim(request.getEmail()))
                    .role("ADMIN")
                    .isActive(true)
                    .passwordResetRequired(false)
                    .build();
            admin = userRepository.save(admin);
        } else {
            boolean factoryPassword = isDefaultAdminPassword(admin);
            if (!factoryPassword) {
                if (isBlank(request.getCurrentPassword())
                        || !passwordEncoder.matches(request.getCurrentPassword(), admin.getPasswordHash())) {
                    throw new IllegalStateException(
                            "Enter the current administrator password before changing the administrator account.");
                }
            } else if (!isBlank(request.getCurrentPassword())
                    && !passwordEncoder.matches(request.getCurrentPassword(), admin.getPasswordHash())) {
                throw new IllegalStateException("The current administrator password is incorrect.");
            }

            String nextUsername = request.getUsername().trim().toLowerCase(Locale.ROOT);
            if (!nextUsername.equals(admin.getUsername())) {
                if (userRepository.existsByUsername(nextUsername)) {
                    throw new IllegalStateException("That username is already in use.");
                }
                admin.setUsername(nextUsername);
            }
            admin.setEmail(trim(request.getEmail()));
            admin.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
            admin.setIsActive(true);
            admin.setPasswordResetRequired(false);
            admin = userRepository.save(admin);
        }

        audit("SETUP_ADMIN_CONFIGURED", "Administrator account configured for " + admin.getUsername());

        // Return a session immediately so the remaining setup steps can run
        // under SETTINGS_MANAGE without an extra login round-trip.
        SystemSettings settings = settingsService.getOrCreate();
        SetupStatusResponse response = buildStatus(settings, admin);
        response.setToken(jwtService.generateToken(admin));
        response.setUserId(admin.getUserId());
        return response;
    }

    @Transactional
    public SetupStatusResponse saveStorage(StorageSetupRequest request) {
        requireAccess();
        SystemSettings settings = settingsService.getOrCreate();
        String previousProvider = settings.getStorageProvider();
        String previousAuthMode = settings.getDriveAuthMode();
        String previousFolder = settings.getDriveFolderId();
        String previousCredentials = settings.getDriveCredentialsPath();
        String previousGoogleAccount = settings.getGoogleAccountEmail();
        String previousGmailBaseUrl = settings.getGmailBaseUrl();

        String provider = normalizeProvider(request.getProvider());
        String authMode = normalizeAuthMode(request.getAuthMode());
        settings.setStorageProvider(provider);
        settings.setDriveAuthMode(authMode);
        settings.setDriveFolderId(trim(request.getFolderId()));
        settings.setDriveBaseUrl(defaultIfBlank(request.getBaseUrl(),
                "MOCK".equals(authMode) ? "http://localhost:9098" : "https://www.googleapis.com"));
        settings.setDriveTokenUri(defaultIfBlank(request.getTokenUri(),
                "https://oauth2.googleapis.com/token"));
        settings.setDriveApplicationName(defaultIfBlank(request.getApplicationName(), "SCMS"));
        settings.setGoogleAccountEmail(trim(request.getGoogleAccountEmail()));
        settings.setGmailBaseUrl(defaultIfBlank(request.getGmailBaseUrl(),
                "MOCK".equals(authMode)
                        ? settings.getDriveBaseUrl()
                        : "https://gmail.googleapis.com"));
        settings.setBackupMode(normalizeBackupMode(request.getBackupMode(), provider));
        if (request.getAutomaticBackups() != null) {
            settings.setBackupEnabled(request.getAutomaticBackups());
        }
        settings.setBackupRetentionDays(request.getRetentionDays() == null ? 30 : request.getRetentionDays());
        settings.setBackupSchedule(defaultIfBlank(request.getSchedule(), "HOURLY").toUpperCase(Locale.ROOT));

        boolean oauthCredentialsStored = false;
        if ("OAUTH".equals(authMode)) {
            if (!isBlank(request.getOauthClientId())
                    && !isBlank(request.getOauthClientSecret())
                    && !isBlank(request.getOauthRefreshToken())) {
                if (!credentialsService.matchesOAuthCredentials(
                        settings.getDriveCredentialsPath(),
                        request.getOauthClientId(),
                        request.getOauthClientSecret(),
                        request.getOauthRefreshToken())) {
                    GoogleDriveCredentialsService.StoredCredential stored =
                            credentialsService.storeOAuthCredentials(
                                    request.getOauthClientId(),
                                    request.getOauthClientSecret(),
                                    request.getOauthRefreshToken());
                    settings.setDriveCredentialsPath(stored.path());
                    googleOAuthTokenService.clear();
                    oauthCredentialsStored = true;
                }
            } else if (!"OAUTH".equalsIgnoreCase(previousAuthMode)) {
                // Switching to OAuth without new credentials must not keep the
                // service-account file, which has a different JSON shape.
                settings.setDriveCredentialsPath(null);
            }
        } else if ("SERVICE_ACCOUNT".equals(authMode)
                && previousAuthMode != null
                && !"SERVICE_ACCOUNT".equalsIgnoreCase(previousAuthMode)) {
            settings.setDriveCredentialsPath(null);
        }

        boolean targetChanged = !equalsIgnoreCase(previousProvider, provider)
                || !equalsIgnoreCase(previousAuthMode, authMode)
                || !equalsIgnoreCase(previousFolder, settings.getDriveFolderId());
        if (targetChanged && !"LOCAL".equals(provider)) {
            // Changing the drive target invalidates the previous connection
            // test. Re-saving identical values keeps it, so the wizard does
            // not lose a successful test when the user clicks Save and continue.
            settings.setDriveConnected(false);
            settings.setDriveLastError(null);
        }

        boolean emailTargetChanged = !equalsIgnoreCase(previousAuthMode, authMode)
                || !equalsIgnoreCase(previousGoogleAccount, settings.getGoogleAccountEmail())
                || !equalsIgnoreCase(previousGmailBaseUrl, settings.getGmailBaseUrl())
                || !equalsIgnoreCase(previousCredentials, settings.getDriveCredentialsPath())
                || oauthCredentialsStored;
        if (emailTargetChanged) {
            // A different Gmail account, endpoint or credential set invalidates
            // the previous profile test and test send.
            settings.setEmailConnected(false);
            settings.setEmailLastError(null);
        }

        // Keep the member-facing sender aligned with the Google account that
        // owns the OAuth token. A manually entered sender is only retained
        // while no Google account has been selected.
        if (!isBlank(settings.getGoogleAccountEmail())
                && (isBlank(settings.getEmailFromAddress())
                        || equalsIgnoreCase(settings.getEmailFromAddress(), previousGoogleAccount))) {
            settings.setEmailFromAddress(settings.getGoogleAccountEmail());
        }

        settingsService.save(settings);

        audit("SETUP_STORAGE_SAVED", "Storage provider " + provider + " / " + authMode);
        return status();
    }

    @Transactional
    public StorageCredentialsResponse uploadServiceAccount(MultipartFile file) {
        requireAccess();
        GoogleDriveCredentialsService.StoredCredential stored = credentialsService.storeServiceAccount(file);
        SystemSettings settings = settingsService.getOrCreate();
        settings.setStorageProvider("GOOGLE_DRIVE");
        settings.setDriveAuthMode("SERVICE_ACCOUNT");
        settings.setDriveCredentialsPath(stored.path());
        settings.setDriveConnected(false);
        settings.setDriveLastError(null);
        // A service account has no Gmail mailbox, so any previous email test
        // no longer applies.
        settings.setEmailConnected(false);
        settings.setEmailLastError(null);
        settingsService.save(settings);
        audit("SETUP_DRIVE_CREDENTIALS_STORED", "Google service-account credentials stored");
        return StorageCredentialsResponse.builder()
                .stored(true)
                .fileName(stored.fileName())
                .message("Service-account credentials stored securely outside the repository.")
                .build();
    }

    @Transactional
    public DriveTestResponse testStorage() {
        requireAccess();
        SystemSettings settings = settingsService.getOrCreate();
        DriveConnectionResult result = googleDriveService.testConnection();
        settings.setDriveConnected(result.connected());
        settings.setDriveLastTestedAt(LocalDateTime.now());
        settings.setDriveLastError(result.connected() ? null : truncate(result.message(), 1000));
        settingsService.save(settings);
        audit(result.connected() ? "SETUP_DRIVE_TEST_OK" : "SETUP_DRIVE_TEST_FAILED",
                result.connected() ? "Google Drive connection verified" : truncate(result.message(), 500));
        return DriveTestResponse.builder()
                .connected(result.connected())
                .message(result.message())
                .folderName(result.folderName())
                .account(result.account())
                .build();
    }

    /**
     * Runs a complete backup from the wizard. Unlike the staff backup API,
     * this stays reachable before go-live so the administrator can prove the
     * automatic engine works while finishing setup.
     */
    public BackupStatusResponse runBackupNow() {
        requireAccess();
        Long actorId = SecurityUtils.currentUserId();
        audit("SETUP_BACKUP_RUN", "Manual backup requested from system setup");
        return backupService.runSetupBackup(actorId == null ? 0L : actorId);
    }

    /**
     * Verifies that the shared Google OAuth token can read the Gmail profile.
     * A successful test records the mailbox address as the account email, so
     * the messaging step can reuse it as the sender identity.
     */
    @Transactional
    public EmailTestResponse testEmail() {
        requireAccess();
        SystemSettings settings = settingsService.getOrCreate();
        GmailConnectionResult result = gmailService.testConnection();

        boolean connected = result.connected();
        String message = result.message();
        if (connected && !isBlank(result.emailAddress())) {
            String configured = settings.getGoogleAccountEmail();
            if (!isBlank(configured) && !equalsIgnoreCase(configured, result.emailAddress())) {
                connected = false;
                message = "The Google connection belongs to " + result.emailAddress()
                        + ", but " + configured + " is recorded as the backup account. "
                        + "Update the Google account email to match, or reconnect with the "
                        + configured + " account.";
            } else {
                settings.setGoogleAccountEmail(result.emailAddress());
                settings.setEmailFromAddress(result.emailAddress());
            }
        }

        LocalDateTime now = LocalDateTime.now();
        settings.setEmailConnected(connected);
        settings.setEmailLastTestedAt(now);
        settings.setEmailLastError(connected ? null : truncate(message, 1000));
        settingsService.save(settings);

        audit(connected ? "SETUP_EMAIL_TEST_OK" : "SETUP_EMAIL_TEST_FAILED",
                connected ? "Gmail account verified: " + result.emailAddress()
                        : truncate(message, 500));

        return EmailTestResponse.builder()
                .connected(connected)
                .message(message)
                .emailAddress(connected ? result.emailAddress() : null)
                .messagesTotal(result.messagesTotal())
                .testedAt(now)
                .lastError(connected ? null : truncate(message, 1000))
                .build();
    }

    /**
     * Sends a real message through Gmail so the operator can confirm delivery
     * before go-live. The recipient defaults to the administrator's account
     * email and then to the connected Google account.
     */
    @Transactional
    public EmailSendResponse sendTestEmail(String to) {
        requireAccess();
        SystemSettings settings = settingsService.getOrCreate();
        User admin = primaryAdmin();
        String recipient = firstNonBlank(to,
                settings.getGoogleAccountEmail(),
                settings.getEmailFromAddress(),
                admin == null ? null : admin.getEmail());
        if (isBlank(recipient)) {
            throw new IllegalStateException(
                    "Enter a test recipient address first. No administrator or Google account email is recorded.");
        }

        String organization = firstNonBlank(settings.getTradingName(), settings.getLegalName(), "SACCO");
        String subject = organization + " email test";
        String body = "This is a test message from " + organization + ".\n\n"
                + "If you received it, Gmail sending is connected and member email reminders can use this account.\n\n"
                + "Sent at " + LocalDateTime.now() + ".";

        GmailSendResult result = gmailService.sendFromSetup(recipient.trim(), subject, body);

        LocalDateTime now = LocalDateTime.now();
        settings.setEmailConnected(true);
        settings.setEmailLastTestedAt(now);
        settings.setEmailLastError(null);
        if (isBlank(settings.getGoogleAccountEmail()) && !isBlank(result.from())) {
            settings.setGoogleAccountEmail(result.from());
            settings.setEmailFromAddress(result.from());
        }
        settingsService.save(settings);

        audit("SETUP_EMAIL_SENT", "Gmail test message sent to " + result.to());

        return EmailSendResponse.builder()
                .messageId(result.messageId())
                .threadId(result.threadId())
                .to(result.to())
                .from(result.from())
                .subject(result.subject())
                .sentAt(now)
                .build();
    }

    @Transactional
    public SetupStatusResponse saveMessaging(MessagingSetupRequest request) {        requireAccess();
        SystemSettings settings = settingsService.getOrCreate();
        settings.setSmsSenderId(trim(request.getSmsSenderId()));
        settings.setWhatsappDisplayName(trim(request.getWhatsappDisplayName()));
        settings.setWhatsappPhoneNumber(trim(request.getWhatsappPhoneNumber()));
        settings.setEmailFromName(trim(request.getEmailFromName()));
        settings.setEmailFromAddress(trim(request.getEmailFromAddress()));
        settings.setEmailReplyTo(trim(request.getEmailReplyTo()));
        settings.setVoiceCallerId(trim(request.getVoiceCallerId()));

        List<Integer> days = request.getReminderDaysBefore() == null
                ? List.of()
                : request.getReminderDaysBefore().stream()
                        .filter(day -> day != null && day >= 0 && day <= 90)
                        .distinct()
                        .sorted(Comparator.reverseOrder())
                        .toList();
        settings.setReminderDaysBefore(days.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(",")));

        LinkedHashSet<String> channels = new LinkedHashSet<>();
        if (request.getReminderChannels() != null) {
            request.getReminderChannels().stream()
                    .filter(channel -> channel != null && CHANNELS.contains(channel.trim().toUpperCase(Locale.ROOT)))
                    .map(channel -> channel.trim().toUpperCase(Locale.ROOT))
                    .forEach(channels::add);
        }
        settings.setReminderChannels(String.join(",", channels));

        // Gmail sends as the connected Google account. Keep the member-facing
        // sender aligned so the address shown in review cannot drift from the
        // mailbox that actually delivers the message.
        if (channels.contains("EMAIL") && !isBlank(settings.getGoogleAccountEmail())) {
            settings.setEmailFromAddress(settings.getGoogleAccountEmail());
        }

        String sendTime = defaultIfBlank(request.getReminderSendTime(), "08:00");
        if (!TIME_PATTERN.matcher(sendTime).matches()) {
            throw new IllegalStateException("Reminder send time must use the 24-hour HH:mm format.");
        }
        settings.setReminderSendTime(sendTime);
        settings.setReminderGraceDays(request.getReminderGraceDays() == null ? 3 : request.getReminderGraceDays());
        settings.setReminderTemplate(defaultIfBlank(request.getReminderTemplate(),
                SystemSettingsService.DEFAULT_REMINDER_TEMPLATE));
        settings.setConsentRequired(request.getConsentRequired() == null ? true : request.getConsentRequired());
        settings.setOptOutText(defaultIfBlank(request.getOptOutText(),
                "Reply STOP to opt out of SMS reminders."));
        settingsService.save(settings);

        audit("SETUP_MESSAGING_SAVED", "Communication senders and loan reminder policy updated");
        return status();
    }

    @Transactional
    public SetupStatusResponse saveBilling(BillingSetupRequest request) {
        requireAccess();
        SystemSettings settings = settingsService.getOrCreate();
        settings.setBillingProvider(trim(request.getProvider()));
        settings.setBillingAccountName(trim(request.getAccountName()));
        settings.setBillingAccountNumber(trim(request.getAccountNumber()));
        settings.setBillingCurrency(defaultIfBlank(request.getCurrency(),
                defaultIfBlank(settings.getCurrency(), "UGX")).toUpperCase(Locale.ROOT));
        settings.setBillingContactEmail(trim(request.getContactEmail()));
        settings.setChargeBearer(defaultIfBlank(request.getChargeBearer(), "SACCO").toUpperCase(Locale.ROOT));
        settings.setSmsUnitCost(zeroIfNull(request.getSmsUnitCost()));
        settings.setWhatsappUnitCost(zeroIfNull(request.getWhatsappUnitCost()));
        settings.setEmailUnitCost(zeroIfNull(request.getEmailUnitCost()));
        settings.setVoiceUnitCost(zeroIfNull(request.getVoiceUnitCost()));
        settings.setLowBalanceThreshold(zeroIfNull(request.getLowBalanceThreshold()));
        settings.setPrepaidBalance(zeroIfNull(request.getPrepaidBalance()));
        settingsService.save(settings);

        audit("SETUP_BILLING_SAVED", "Communication billing account and rates updated");
        return status();
    }

    @Transactional
    public SetupStatusResponse completeSetup() {
        requireAccess();
        SystemSettings settings = settingsService.getOrCreate();
        User admin = primaryAdmin();
        List<String> missing = missingFields(settings, admin);
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Complete these setup items first: " + String.join(", ", missing));
        }

        settings.setSetupCompleted(true);
        settings.setSetupCompletedAt(LocalDateTime.now());
        settingsService.save(settings);
        audit("SETUP_COMPLETED", "System setup completed; the application is ready for official use");
        return status();
    }

    /**
     * Returns the administrator the setup should manage. An active account is
     * always preferred so an inactive recovery record cannot be selected just
     * because it happens to have a lower identifier.
     */
    private User primaryAdmin() {
        return userRepository.findFirstByRoleAndIsActiveTrueOrderByUserIdAsc("ADMIN")
                .or(() -> userRepository.findFirstByRoleOrderByUserIdAsc("ADMIN"))
                .orElse(null);
    }

    private void requireAccess() {
        // Anonymous setup is limited to a brand-new installation with no
        // administrator. Once an administrator exists, every change needs a
        // signed-in SETTINGS_MANAGE session even if setup is still incomplete.
        if (settingsService.isSetupCompleted() || userRepository.existsByRole("ADMIN")) {
            Long actorId = SecurityUtils.currentUserId();
            if (actorId == null) {
                throw new AccessDeniedException(
                        "Sign in with an administrator account to continue system setup.");
            }
            permissionService.require(actorId, Permission.SETTINGS_MANAGE);
        }
    }

    private boolean canManageSettings() {
        Long actorId = SecurityUtils.currentUserId();
        if (actorId == null) {
            return false;
        }
        return userRepository.findById(actorId)
                .map(user -> Boolean.TRUE.equals(user.getIsActive())
                        && permissionService.permissionsForRole(user.getRole())
                                .contains(Permission.SETTINGS_MANAGE))
                .orElse(false);
    }

    private List<String> missingFields(SystemSettings settings, User admin) {
        List<String> missing = new ArrayList<>();
        if (isBlank(settings.getLegalName())) missing.add("Organisation legal name");
        if (isBlank(settings.getAddressLine1())) missing.add("Organisation address");
        if (isBlank(settings.getPhone())) missing.add("Organisation phone");
        if (isBlank(settings.getEmail())) missing.add("Organisation email");

        if (admin == null) {
            missing.add("Administrator account");
        } else if (isDefaultAdminPassword(admin)) {
            missing.add("Administrator password change");
        }

        if (!"LOCAL".equalsIgnoreCase(settings.getStorageProvider())) {
            if (isBlank(settings.getDriveFolderId())) missing.add("Google Drive folder ID");
            if (!"MOCK".equalsIgnoreCase(settings.getDriveAuthMode())
                    && !credentialsService.exists(settings.getDriveCredentialsPath())) {
                missing.add("Google Drive credentials");
            }
            if (!Boolean.TRUE.equals(settings.getDriveConnected())) {
                missing.add("Google Drive connection test");
            }
        }

        List<String> channels = splitChannels(settings.getReminderChannels());
        if (channels.isEmpty()) {
            missing.add("At least one loan reminder channel");
        } else {
            if (channels.contains("SMS") && isBlank(settings.getSmsSenderId())) {
                missing.add("SMS sender ID");
            }
            if (channels.contains("WHATSAPP") && isBlank(settings.getWhatsappPhoneNumber())) {
                missing.add("WhatsApp sender number");
            }
            if (channels.contains("EMAIL")) {
                if (isBlank(settings.getGoogleAccountEmail())
                        && isBlank(settings.getEmailFromAddress())) {
                    missing.add("Google account email for Gmail");
                }
                if (!"OAUTH".equalsIgnoreCase(settings.getDriveAuthMode())
                        && !"MOCK".equalsIgnoreCase(settings.getDriveAuthMode())) {
                    missing.add("Google OAuth connection for Gmail");
                } else if (!Boolean.TRUE.equals(settings.getEmailConnected())) {
                    missing.add("Gmail connection test");
                }
            }
            if (channels.contains("VOICE") && isBlank(settings.getVoiceCallerId())) {
                missing.add("Voice caller ID");
            }
        }
        if (isBlank(settings.getReminderDaysBefore())) missing.add("Loan reminder schedule");
        if (isBlank(settings.getReminderTemplate())) missing.add("Loan reminder message template");
        if (Boolean.TRUE.equals(settings.getConsentRequired()) && isBlank(settings.getOptOutText())) {
            missing.add("Opt-out wording");
        }

        if (isBlank(settings.getBillingAccountName())) missing.add("Communication billing account name");
        if (isBlank(settings.getBillingAccountNumber())) missing.add("Communication billing account number");
        if (isBlank(settings.getBillingCurrency())) missing.add("Communication billing currency");
        if (settings.getSmsUnitCost() == null
                || settings.getWhatsappUnitCost() == null
                || settings.getEmailUnitCost() == null
                || settings.getVoiceUnitCost() == null) {
            missing.add("Per-channel communication rates");
        }
        return missing;
    }

    private SetupStatusResponse.Organization organization(SystemSettings s) {
        return SetupStatusResponse.Organization.builder()
                .legalName(s.getLegalName())
                .tradingName(s.getTradingName())
                .registrationNumber(s.getRegistrationNumber())
                .taxPin(s.getTaxPin())
                .addressLine1(s.getAddressLine1())
                .addressLine2(s.getAddressLine2())
                .city(s.getCity())
                .country(s.getCountry())
                .phone(s.getPhone())
                .email(s.getEmail())
                .website(s.getWebsite())
                .timezone(s.getTimezone())
                .currency(s.getCurrency())
                .build();
    }

    private SetupStatusResponse.Storage storage(SystemSettings s) {
        return SetupStatusResponse.Storage.builder()
                .provider(s.getStorageProvider())
                .authMode(s.getDriveAuthMode())
                .folderId(s.getDriveFolderId())
                .googleAccountEmail(s.getGoogleAccountEmail())
                .gmailBaseUrl(s.getGmailBaseUrl())
                .baseUrl(s.getDriveBaseUrl())
                .applicationName(s.getDriveApplicationName())
                .credentialsConfigured("MOCK".equalsIgnoreCase(s.getDriveAuthMode())
                        || credentialsService.exists(s.getDriveCredentialsPath()))
                .credentialsFileName(credentialsService.fileName(s.getDriveCredentialsPath()))
                .connected(Boolean.TRUE.equals(s.getDriveConnected()))
                .lastTestedAt(s.getDriveLastTestedAt())
                .lastError(s.getDriveLastError())
                .emailConnected(Boolean.TRUE.equals(s.getEmailConnected()))
                .emailLastTestedAt(s.getEmailLastTestedAt())
                .emailLastError(s.getEmailLastError())
                .backupMode(s.getBackupMode())
                .effectiveBackupMode(settingsService.effectiveBackupMode())
                .automaticBackupsEnabled(settingsService.isAutomaticBackupEnabled())
                .retentionDays(s.getBackupRetentionDays())
                .schedule(s.getBackupSchedule())
                .backup(backupService.setupSummary())
                .build();
    }

    private SetupStatusResponse.Messaging messaging(SystemSettings s) {
        return SetupStatusResponse.Messaging.builder()
                .smsSenderId(s.getSmsSenderId())
                .whatsappDisplayName(s.getWhatsappDisplayName())
                .whatsappPhoneNumber(s.getWhatsappPhoneNumber())
                .emailFromName(s.getEmailFromName())
                .emailFromAddress(s.getEmailFromAddress())
                .emailReplyTo(s.getEmailReplyTo())
                .voiceCallerId(s.getVoiceCallerId())
                .reminderDaysBefore(parseDays(s.getReminderDaysBefore()))
                .reminderChannels(splitChannels(s.getReminderChannels()))
                .reminderSendTime(s.getReminderSendTime())
                .reminderGraceDays(s.getReminderGraceDays())
                .reminderTemplate(s.getReminderTemplate())
                .consentRequired(s.getConsentRequired())
                .optOutText(s.getOptOutText())
                .build();
    }

    private SetupStatusResponse.Billing billing(SystemSettings s) {
        return SetupStatusResponse.Billing.builder()
                .provider(s.getBillingProvider())
                .accountName(s.getBillingAccountName())
                .accountNumber(s.getBillingAccountNumber())
                .currency(s.getBillingCurrency())
                .contactEmail(s.getBillingContactEmail())
                .chargeBearer(s.getChargeBearer())
                .smsUnitCost(s.getSmsUnitCost())
                .whatsappUnitCost(s.getWhatsappUnitCost())
                .emailUnitCost(s.getEmailUnitCost())
                .voiceUnitCost(s.getVoiceUnitCost())
                .lowBalanceThreshold(s.getLowBalanceThreshold())
                .prepaidBalance(s.getPrepaidBalance())
                .build();
    }

    private boolean isDefaultAdminPassword(User admin) {
        return admin != null && admin.getPasswordHash() != null
                && passwordEncoder.matches(DEFAULT_ADMIN_PASSWORD, admin.getPasswordHash());
    }

    private List<Integer> parseDays(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .map(part -> {
                    try {
                        return Integer.parseInt(part);
                    } catch (NumberFormatException ex) {
                        return null;
                    }
                })
                .filter(day -> day != null && day >= 0 && day <= 90)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .toList();
    }

    private List<String> splitChannels(String value) {
        if (isBlank(value)) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(channel -> CHANNELS.contains(channel.toUpperCase(Locale.ROOT)))
                .map(channel -> channel.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private String normalizeProvider(String value) {
        String normalized = defaultIfBlank(value, "GOOGLE_DRIVE").toUpperCase(Locale.ROOT);
        return "LOCAL".equals(normalized) ? "LOCAL" : "GOOGLE_DRIVE";
    }

    private String normalizeAuthMode(String value) {
        String normalized = defaultIfBlank(value, "SERVICE_ACCOUNT").toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "OAUTH", "MOCK", "SERVICE_ACCOUNT" -> normalized;
            default -> "SERVICE_ACCOUNT";
        };
    }

    private String normalizeBackupMode(String value, String provider) {
        if ("LOCAL".equals(provider)) {
            String normalized = defaultIfBlank(value, "QUEUE_ONLY").toUpperCase(Locale.ROOT);
            return switch (normalized) {
                case "LOCAL", "QUEUE_ONLY", "DIRECTORY", "HTTP" -> normalized;
                default -> "QUEUE_ONLY";
            };
        }
        return "GOOGLE_DRIVE";
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private void audit(String action, String reason) {
        Long actorId = SecurityUtils.currentUserId();
        auditService.logEvent(actorId, action, "SYSTEM_SETTINGS", 1L, reason);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return right != null && left.trim().equalsIgnoreCase(right.trim());
    }
}
