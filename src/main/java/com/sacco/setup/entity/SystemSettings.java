package com.sacco.setup.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Single-row go-live configuration for this installation. Secrets are kept
 * outside the database: Google credentials are written to a local secrets
 * directory and only the path is stored here.
 */
@Entity
@Table(name = "system_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SystemSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "settings_id")
    private Long settingsId;

    // ---- Organisation profile -------------------------------------------

    @Column(name = "legal_name", length = 200)
    private String legalName;

    @Column(name = "trading_name", length = 200)
    private String tradingName;

    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    @Column(name = "tax_pin", length = 100)
    private String taxPin;

    @Column(name = "address_line1", length = 255)
    private String addressLine1;

    @Column(name = "address_line2", length = 255)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "email", length = 200)
    private String email;

    @Column(name = "website", length = 255)
    private String website;

    @Column(name = "timezone", length = 100)
    private String timezone;

    @Column(name = "currency", length = 10)
    private String currency;

    // ---- Storage / Google Drive -----------------------------------------

    @Column(name = "storage_provider", length = 30)
    private String storageProvider;

    @Column(name = "drive_auth_mode", length = 30)
    private String driveAuthMode;

    @Column(name = "drive_base_url", length = 500)
    private String driveBaseUrl;

    @Column(name = "drive_token_uri", length = 500)
    private String driveTokenUri;

    @Column(name = "drive_application_name", length = 200)
    private String driveApplicationName;

    @Column(name = "drive_credentials_path", length = 1000)
    private String driveCredentialsPath;

    @Column(name = "google_account_email", length = 255)
    private String googleAccountEmail;

    @Column(name = "gmail_base_url", length = 500)
    private String gmailBaseUrl;

    @Column(name = "email_connected", nullable = false)
    @Builder.Default
    private Boolean emailConnected = false;

    @Column(name = "email_last_tested_at")
    private LocalDateTime emailLastTestedAt;

    @Column(name = "email_last_error", length = 1000)
    private String emailLastError;

    @Column(name = "drive_folder_id", length = 255)
    private String driveFolderId;

    @Column(name = "drive_connected", nullable = false)
    @Builder.Default
    private Boolean driveConnected = false;

    @Column(name = "drive_last_tested_at")
    private LocalDateTime driveLastTestedAt;

    @Column(name = "drive_last_error", length = 1000)
    private String driveLastError;

    @Column(name = "backup_mode", length = 30)
    private String backupMode;

    @Column(name = "backup_enabled", nullable = false)
    @Builder.Default
    private Boolean backupEnabled = true;

    @Column(name = "backup_retention_days")
    private Integer backupRetentionDays;

    @Column(name = "backup_schedule", length = 50)
    private String backupSchedule;

    // ---- Messaging sender identities and reminder policy ----------------

    @Column(name = "sms_sender_id", length = 50)
    private String smsSenderId;

    @Column(name = "whatsapp_display_name", length = 200)
    private String whatsappDisplayName;

    @Column(name = "whatsapp_phone_number", length = 50)
    private String whatsappPhoneNumber;

    @Column(name = "email_from_name", length = 200)
    private String emailFromName;

    @Column(name = "email_from_address", length = 200)
    private String emailFromAddress;

    @Column(name = "email_reply_to", length = 200)
    private String emailReplyTo;

    @Column(name = "voice_caller_id", length = 50)
    private String voiceCallerId;

    @Column(name = "reminder_days_before", length = 100)
    private String reminderDaysBefore;

    @Column(name = "reminder_channels", length = 100)
    private String reminderChannels;

    @Column(name = "reminder_send_time", length = 10)
    private String reminderSendTime;

    @Column(name = "reminder_grace_days")
    private Integer reminderGraceDays;

    @Column(name = "reminder_template", length = 2000)
    private String reminderTemplate;

    @Column(name = "consent_required", nullable = false)
    @Builder.Default
    private Boolean consentRequired = true;

    @Column(name = "opt_out_text", length = 500)
    private String optOutText;

    // ---- Communication billing ------------------------------------------

    @Column(name = "billing_provider", length = 100)
    private String billingProvider;

    @Column(name = "billing_account_name", length = 200)
    private String billingAccountName;

    @Column(name = "billing_account_number", length = 100)
    private String billingAccountNumber;

    @Column(name = "billing_currency", length = 10)
    private String billingCurrency;

    @Column(name = "billing_contact_email", length = 200)
    private String billingContactEmail;

    @Column(name = "charge_bearer", length = 30)
    private String chargeBearer;

    @Column(name = "sms_unit_cost", precision = 14, scale = 4)
    private BigDecimal smsUnitCost;

    @Column(name = "whatsapp_unit_cost", precision = 14, scale = 4)
    private BigDecimal whatsappUnitCost;

    @Column(name = "email_unit_cost", precision = 14, scale = 4)
    private BigDecimal emailUnitCost;

    @Column(name = "voice_unit_cost", precision = 14, scale = 4)
    private BigDecimal voiceUnitCost;

    @Column(name = "low_balance_threshold", precision = 14, scale = 2)
    private BigDecimal lowBalanceThreshold;

    @Column(name = "prepaid_balance", precision = 14, scale = 2)
    private BigDecimal prepaidBalance;

    // ---- Setup lifecycle -------------------------------------------------

    @Column(name = "setup_completed", nullable = false)
    @Builder.Default
    private Boolean setupCompleted = false;

    @Column(name = "setup_completed_at")
    private LocalDateTime setupCompletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
