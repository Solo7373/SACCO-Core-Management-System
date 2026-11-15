-- Africa's Talking SMS integration.
--
-- The outbox pattern is used for outgoing messages: API callers persist a row
-- as QUEUED and a scheduled dispatcher claims it with FOR UPDATE SKIP LOCKED.
-- This keeps HTTP requests fast, survives restarts and allows safe horizontal
-- scaling of the dispatcher.
--
-- Delivery reports and incoming messages are stored verbatim (bounded to 16 KB
-- by the webhook parser) so support staff can audit exactly what the provider
-- sent, even when a message can no longer be matched to an outbox row.

CREATE TABLE sms_messages (
    message_id             BIGSERIAL PRIMARY KEY,
    reference              VARCHAR(36) NOT NULL UNIQUE,
    batch_id               VARCHAR(36),
    recipient              VARCHAR(20) NOT NULL,
    sender_id              VARCHAR(20),
    body                   TEXT NOT NULL,
    message_type           VARCHAR(30) NOT NULL DEFAULT 'NOTIFICATION',
    status                 VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    provider_message_id    VARCHAR(100),
    provider_status        VARCHAR(40),
    provider_status_code   INTEGER,
    provider_cost          VARCHAR(40),
    failure_reason         VARCHAR(500),
    attempts               INTEGER NOT NULL DEFAULT 0,
    max_attempts           INTEGER NOT NULL DEFAULT 5,
    next_attempt_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_attempt_at        TIMESTAMP,
    sent_at                TIMESTAMP,
    delivered_at           TIMESTAMP,
    member_id              BIGINT,
    created_by             BIGINT,
    version                BIGINT NOT NULL DEFAULT 0,
    created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sms_messages_status_next ON sms_messages(status, next_attempt_at);
CREATE INDEX idx_sms_messages_recipient ON sms_messages(recipient);
CREATE INDEX idx_sms_messages_provider_id ON sms_messages(provider_message_id);
CREATE INDEX idx_sms_messages_batch ON sms_messages(batch_id);
CREATE INDEX idx_sms_messages_created ON sms_messages(created_at);

CREATE TABLE sms_delivery_reports (
    report_id             BIGSERIAL PRIMARY KEY,
    provider_message_id   VARCHAR(100),
    status                VARCHAR(40),
    phone_number          VARCHAR(20),
    network_code          VARCHAR(20),
    failure_reason        VARCHAR(500),
    retry_count           INTEGER,
    raw_payload           TEXT,
    processed             BOOLEAN NOT NULL DEFAULT FALSE,
    received_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sms_delivery_reports_message ON sms_delivery_reports(provider_message_id);
CREATE INDEX idx_sms_delivery_reports_processed ON sms_delivery_reports(processed, received_at);
CREATE INDEX idx_sms_delivery_reports_received ON sms_delivery_reports(received_at);

CREATE TABLE incoming_sms (
    incoming_id           BIGSERIAL PRIMARY KEY,
    provider_message_id   VARCHAR(100) NOT NULL UNIQUE,
    sender                VARCHAR(30),
    recipient             VARCHAR(30),
    text                  TEXT,
    link_id               VARCHAR(100),
    received_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    raw_payload           TEXT,
    processed             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_incoming_sms_sender ON incoming_sms(sender);
CREATE INDEX idx_incoming_sms_received ON incoming_sms(received_at);

CREATE TABLE sms_opt_outs (
    opt_out_id            BIGSERIAL PRIMARY KEY,
    phone_number          VARCHAR(20) NOT NULL UNIQUE,
    active                BOOLEAN NOT NULL DEFAULT TRUE,
    reason                VARCHAR(200),
    source                VARCHAR(30),
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sms_opt_outs_active ON sms_opt_outs(active);

CREATE TABLE phone_verifications (
    verification_id       BIGSERIAL PRIMARY KEY,
    reference             VARCHAR(36) NOT NULL UNIQUE,
    phone_number          VARCHAR(20) NOT NULL,
    purpose               VARCHAR(40) NOT NULL,
    code_hash             VARCHAR(64) NOT NULL,
    expires_at            TIMESTAMP NOT NULL,
    attempts              INTEGER NOT NULL DEFAULT 0,
    max_attempts          INTEGER NOT NULL DEFAULT 5,
    consumed_at           TIMESTAMP,
    verified_at           TIMESTAMP,
    requested_by          BIGINT,
    request_ip            VARCHAR(45),
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_phone_verifications_lookup
    ON phone_verifications(phone_number, purpose, created_at);
CREATE INDEX idx_phone_verifications_ip
    ON phone_verifications(request_ip, created_at);
CREATE INDEX idx_phone_verifications_expiry
    ON phone_verifications(expires_at);

-- At most one live code per phone/purpose. Expired codes are invalidated by
-- the service before a new code is issued, and this index closes the race
-- between two simultaneous requests.
CREATE UNIQUE INDEX uq_phone_verifications_active
    ON phone_verifications(phone_number, purpose)
    WHERE consumed_at IS NULL;
