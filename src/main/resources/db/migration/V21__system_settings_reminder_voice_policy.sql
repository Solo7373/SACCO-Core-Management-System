-- ============================================================
-- V21: Reminder channel/voice policy columns on system_settings.
--
-- V16 created the reminder policy and sender identity columns, and
-- V19 added the reminder/voice tables, but the WhatsApp reminder
-- template, its language and the voice calling switches were never
-- added to the settings row. This migration completes the schema so
-- Hibernate validation passes and the setup wizard can persist them.
-- ============================================================

ALTER TABLE system_settings
    ADD COLUMN IF NOT EXISTS reminder_whatsapp_template VARCHAR(200),
    ADD COLUMN IF NOT EXISTS reminder_whatsapp_language VARCHAR(20),
    ADD COLUMN IF NOT EXISTS voice_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS voice_quiet_hours_start VARCHAR(5),
    ADD COLUMN IF NOT EXISTS voice_quiet_hours_end VARCHAR(5);

COMMENT ON COLUMN system_settings.reminder_whatsapp_template IS 'Approved Meta WhatsApp utility template used for automatic loan reminders.';
COMMENT ON COLUMN system_settings.reminder_whatsapp_language IS 'Language code for the approved WhatsApp reminder template, for example en.';
COMMENT ON COLUMN system_settings.voice_enabled IS 'Master switch for automated outbound voice reminders through Africa''s Talking.';
COMMENT ON COLUMN system_settings.voice_quiet_hours_start IS 'Local time (HH:mm) before which voice reminders are not placed.';
COMMENT ON COLUMN system_settings.voice_quiet_hours_end IS 'Local time (HH:mm) after which voice reminders are not placed.';
