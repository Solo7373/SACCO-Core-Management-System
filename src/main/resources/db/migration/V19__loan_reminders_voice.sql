-- ============================================================
-- V19: Loan due-date reminders and Africa Talking voice calls.
--
-- loan_reminders: one row per (loan, instalment, days-before-due,
-- channel, send-date). The unique index is the idempotency guard for
-- the scheduled reminder engine: a reminder can never be dispatched
-- twice for the same trigger.
--
-- voice_calls / voice_call_events: outbound Africa Talking voice
-- sessions triggered manually or by the reminder engine, plus the
-- asynchronous call-state callbacks.
-- ============================================================

CREATE TABLE loan_reminders (
    reminder_id         BIGSERIAL PRIMARY KEY,
    loan_id             BIGINT NOT NULL,
    schedule_id         BIGINT NOT NULL,
    installment_no      INTEGER NOT NULL,
    due_date            DATE NOT NULL,
    days_before         INTEGER NOT NULL,
    send_date           DATE NOT NULL,
    channel             VARCHAR(20) NOT NULL,
    recipient           VARCHAR(20) NOT NULL,
    member_id           BIGINT,
    loan_number         VARCHAR(32) NOT NULL,
    amount              NUMERIC(15,2) NOT NULL,
    currency            VARCHAR(10) NOT NULL DEFAULT 'UGX',
    rendered_message    TEXT NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    provider_message_id VARCHAR(100),
    failure_reason      VARCHAR(500),
    sent_at             TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          BIGINT
);

CREATE UNIQUE INDEX uq_loan_reminders_once
    ON loan_reminders (loan_id, schedule_id, days_before, channel, send_date);

CREATE INDEX idx_loan_reminders_loan_due ON loan_reminders (loan_id, due_date);
CREATE INDEX idx_loan_reminders_channel_status ON loan_reminders (channel, status, created_at);

CREATE TABLE voice_calls (
    call_id             BIGSERIAL PRIMARY KEY,
    loan_id             BIGINT,
    member_id           BIGINT,
    to_phone            VARCHAR(20) NOT NULL,
    from_caller_id      VARCHAR(50),
    status              VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    provider_call_id    VARCHAR(100),
    provider_status     VARCHAR(40),
    duration_seconds    INTEGER,
    cost                VARCHAR(40),
    failure_reason      VARCHAR(500),
    initiated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    answered_at         TIMESTAMP,
    ended_at            TIMESTAMP,
    created_by          BIGINT
);

CREATE INDEX idx_voice_calls_loan ON voice_calls (loan_id, initiated_at);
CREATE INDEX idx_voice_calls_status ON voice_calls (status, initiated_at);

CREATE TABLE voice_call_events (
    event_id            BIGSERIAL PRIMARY KEY,
    call_id             BIGINT NOT NULL,
    event_type          VARCHAR(40) NOT NULL,
    status              VARCHAR(20),
    payload             TEXT,
    duration_seconds    INTEGER,
    cost                VARCHAR(40),
    occurred_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (call_id) REFERENCES voice_calls(call_id)
);

CREATE INDEX idx_voice_call_events_call ON voice_call_events (call_id, occurred_at);
CREATE INDEX idx_voice_call_events_type ON voice_call_events (event_type, occurred_at);
