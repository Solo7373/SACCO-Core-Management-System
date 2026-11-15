-- ============================================================
-- V11: Fine-grained RBAC, member soft-delete / restoration and
--      the 3-tier hybrid resilience backup engine.
-- ============================================================

-- ------------------------------------------------------------
-- Member lifecycle: archive instead of delete. Financial rows and
-- foreign keys are never removed; only the linked login is sealed.
-- ------------------------------------------------------------
ALTER TABLE members
    ADD COLUMN is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN deleted_at TIMESTAMP,
    ADD COLUMN deletion_reason VARCHAR(500);

CREATE INDEX idx_members_is_deleted ON members(is_deleted);

-- ------------------------------------------------------------
-- Credential sealing. The previous username is retained so a
-- one-click restore can bring the account back, but the restored
-- user must set a new password before signing in.
-- ------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN password_reset_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN archived_username VARCHAR(50);

-- ------------------------------------------------------------
-- Tier 3 cloud queue. Jobs survive power / Wi-Fi outages because
-- they live in PostgreSQL, not in memory. The scheduled worker
-- resumes uploads as soon as connectivity returns.
-- ------------------------------------------------------------
CREATE TABLE backup_jobs (
    job_id            BIGSERIAL PRIMARY KEY,
    tier              VARCHAR(20) NOT NULL,
    file_name         VARCHAR(255) NOT NULL,
    local_path        VARCHAR(1000),
    target_path       VARCHAR(1000),
    status            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts          INTEGER NOT NULL DEFAULT 0,
    size_bytes        BIGINT,
    checksum          VARCHAR(64),
    error_message     VARCHAR(2000),
    next_attempt_at   TIMESTAMP,
    last_attempt_at   TIMESTAMP,
    completed_at      TIMESTAMP,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_backup_jobs_status ON backup_jobs(status, next_attempt_at);
CREATE INDEX idx_backup_jobs_created ON backup_jobs(created_at DESC);
CREATE INDEX idx_backup_jobs_tier ON backup_jobs(tier, created_at DESC);
