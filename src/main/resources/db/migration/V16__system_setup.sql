-- ============================================================
-- V16: First-run system setup and go-live configuration.
--
-- One row stores the organisation identity, Google Drive storage
-- settings, the sender identities used for member reminders and the
-- communication billing account/rates. Secrets are never stored in
-- this table: Google credentials live in a local secrets directory
-- and only their path is recorded here.
-- ============================================================

CREATE TABLE system_settings (
    settings_id             BIGSERIAL PRIMARY KEY,

    -- Organisation profile
    legal_name              VARCHAR(200),
    trading_name            VARCHAR(200),
    registration_number     VARCHAR(100),
    tax_pin                 VARCHAR(100),
    address_line1           VARCHAR(255),
    address_line2           VARCHAR(255),
    city                    VARCHAR(100),
    country                 VARCHAR(100),
    phone                   VARCHAR(50),
    email                   VARCHAR(200),
    website                 VARCHAR(255),
    timezone                VARCHAR(100) DEFAULT 'Africa/Kampala',
    currency                VARCHAR(10) DEFAULT 'UGX',

    -- Storage / Google Drive
    storage_provider        VARCHAR(30) DEFAULT 'GOOGLE_DRIVE',
    drive_auth_mode         VARCHAR(30) DEFAULT 'SERVICE_ACCOUNT',
    drive_base_url          VARCHAR(500),
    drive_token_uri         VARCHAR(500),
    drive_application_name  VARCHAR(200) DEFAULT 'SCMS',
    drive_credentials_path  VARCHAR(1000),
    drive_folder_id         VARCHAR(255),
    drive_connected         BOOLEAN NOT NULL DEFAULT FALSE,
    drive_last_tested_at    TIMESTAMP,
    drive_last_error        VARCHAR(1000),
    backup_mode             VARCHAR(30) DEFAULT 'GOOGLE_DRIVE',
    backup_retention_days   INTEGER DEFAULT 30,
    backup_schedule         VARCHAR(50) DEFAULT 'HOURLY',

    -- Messaging sender identities and loan reminder policy
    sms_sender_id           VARCHAR(50),
    whatsapp_display_name   VARCHAR(200),
    whatsapp_phone_number   VARCHAR(50),
    email_from_name         VARCHAR(200),
    email_from_address      VARCHAR(200),
    email_reply_to          VARCHAR(200),
    voice_caller_id         VARCHAR(50),
    reminder_days_before    VARCHAR(100) DEFAULT '5,3,1,0',
    reminder_channels       VARCHAR(100) DEFAULT 'SMS,WHATSAPP',
    reminder_send_time      VARCHAR(10) DEFAULT '08:00',
    reminder_grace_days     INTEGER DEFAULT 3,
    reminder_template       VARCHAR(2000),
    consent_required        BOOLEAN NOT NULL DEFAULT TRUE,
    opt_out_text            VARCHAR(500),

    -- Communication billing account and per-channel charges
    billing_provider        VARCHAR(100),
    billing_account_name    VARCHAR(200),
    billing_account_number  VARCHAR(100),
    billing_currency        VARCHAR(10) DEFAULT 'UGX',
    billing_contact_email   VARCHAR(200),
    charge_bearer           VARCHAR(30) DEFAULT 'SACCO',
    sms_unit_cost           NUMERIC(14,4) DEFAULT 0,
    whatsapp_unit_cost      NUMERIC(14,4) DEFAULT 0,
    email_unit_cost         NUMERIC(14,4) DEFAULT 0,
    voice_unit_cost         NUMERIC(14,4) DEFAULT 0,
    low_balance_threshold   NUMERIC(14,2) DEFAULT 0,
    prepaid_balance         NUMERIC(14,2) DEFAULT 0,

    -- Setup lifecycle
    setup_completed         BOOLEAN NOT NULL DEFAULT FALSE,
    setup_completed_at      TIMESTAMP,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_system_settings_completed ON system_settings(setup_completed);
