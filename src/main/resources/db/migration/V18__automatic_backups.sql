-- ============================================================
-- V18: Activate automatic (WhatsApp-style) backups.
--
-- The first-run setup already stores the backup schedule and
-- retention period. This flag lets the operator switch the
-- background scheduler on or off without changing the schedule.
-- A complete pg_dump snapshot includes member records and their
-- contact names, savings, loans, SMS and WhatsApp messages.
-- ============================================================

ALTER TABLE system_settings
    ADD COLUMN backup_enabled BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN system_settings.backup_enabled IS
    'When true, the background scheduler runs a complete database snapshot on backup_schedule.';
