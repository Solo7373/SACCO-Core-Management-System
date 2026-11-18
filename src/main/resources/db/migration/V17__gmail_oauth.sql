-- ============================================================
-- V17: Gmail OAuth for outbound email.
--
-- The Google account chosen during storage setup can also send
-- member email. Only the Gmail address and connection state are
-- stored here; the OAuth client credentials and refresh token
-- remain in the protected credentials file recorded by V16.
-- ============================================================

ALTER TABLE system_settings
    ADD COLUMN google_account_email VARCHAR(255),
    ADD COLUMN gmail_base_url VARCHAR(500) DEFAULT 'https://gmail.googleapis.com',
    ADD COLUMN email_connected BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN email_last_tested_at TIMESTAMP,
    ADD COLUMN email_last_error VARCHAR(1000);

COMMENT ON COLUMN system_settings.google_account_email IS
    'Gmail address that owns the Drive folder and sends outbound email.';
COMMENT ON COLUMN system_settings.gmail_base_url IS
    'Gmail API base URL; the local mock server can override this during testing.';
COMMENT ON COLUMN system_settings.email_connected IS
    'True after a successful Gmail profile test or test send.';
