-- ============================================================
-- V22: Audit timestamp on outbound voice calls.
--
-- V19 created voice_calls without created_at, while the JPA entity
-- maps it with @CreationTimestamp. Add the column with a default so
-- existing rows remain valid and Hibernate schema validation passes.
-- ============================================================

ALTER TABLE voice_calls
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP;

COMMENT ON COLUMN voice_calls.created_at IS 'Row creation timestamp for audit and reporting.';
