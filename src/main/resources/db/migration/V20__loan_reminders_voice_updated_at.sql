-- ============================================================
-- V20: Add updated_at columns required by the JPA entities for
-- the loan reminder outbox and the Africa Talking voice calls.
--
-- V19 created the loan_reminders / voice_calls / voice_call_events
-- tables but did not include the optimistic-locking columns that the
-- JPA entities map via @UpdateTimestamp. This migration completes
-- the schema so Hibernate schema validation passes again.
-- ============================================================

ALTER TABLE loan_reminders
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE voice_calls
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

COMMENT ON COLUMN loan_reminders.updated_at IS 'Last row modification timestamp used for optimistic locking.';
COMMENT ON COLUMN voice_calls.updated_at IS 'Last row modification timestamp used for optimistic locking.';
