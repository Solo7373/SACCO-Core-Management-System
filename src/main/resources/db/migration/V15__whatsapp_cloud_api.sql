-- Meta WhatsApp Cloud API integration.
--
-- WhatsApp is a separate channel from SMS. Outgoing messages use the same
-- transactional outbox pattern: API callers persist a row as QUEUED and the
-- scheduled dispatcher claims it with FOR UPDATE SKIP LOCKED. Meta status
-- callbacks advance the row monotonically (QUEUED -> SENDING -> SENT ->
-- DELIVERED -> READ); a late failure can never overwrite a confirmed delivery.
--
-- Templates must be approved by Meta before a business-initiated conversation
-- is allowed, so a local mirror of the WABA template list is maintained.
-- Inbound messages and webhook events are stored verbatim (bounded by the
-- webhook parser) for support and audit, even when they cannot be matched to
-- an outbox row.

CREATE TABLE whatsapp_messages (
    message_id             BIGSERIAL PRIMARY KEY,
    reference              VARCHAR(36) NOT NULL UNIQUE,
    batch_id               VARCHAR(36),
    recipient              VARCHAR(20) NOT NULL,
    recipient_wa_id        VARCHAR(20),
    message_type           VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    template_name          VARCHAR(512),
    template_language      VARCHAR(20),
    template_parameters    TEXT,
    body                   TEXT,
    media_url              TEXT,
    media_id               VARCHAR(128),
    caption                TEXT,
    context_message_id     VARCHAR(128),
    status                 VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    provider_message_id    VARCHAR(128),
    provider_status        VARCHAR(40),
    error_code             INTEGER,
    error_subcode          INTEGER,
    error_title            VARCHAR(255),
    error_message          TEXT,
    fbtrace_id             VARCHAR(128),
    attempts               INTEGER NOT NULL DEFAULT 0,
    max_attempts           INTEGER NOT NULL DEFAULT 5,
    next_attempt_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_attempt_at        TIMESTAMP,
    sent_at                TIMESTAMP,
    delivered_at           TIMESTAMP,
    read_at                TIMESTAMP,
    failed_at              TIMESTAMP,
    member_id              BIGINT,
    created_by             BIGINT,
    version                BIGINT NOT NULL DEFAULT 0,
    created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_whatsapp_messages_status_next
    ON whatsapp_messages(status, next_attempt_at);
CREATE INDEX idx_whatsapp_messages_recipient ON whatsapp_messages(recipient);
CREATE INDEX idx_whatsapp_messages_provider_id
    ON whatsapp_messages(provider_message_id);
CREATE INDEX idx_whatsapp_messages_batch ON whatsapp_messages(batch_id);
CREATE INDEX idx_whatsapp_messages_created ON whatsapp_messages(created_at);

CREATE TABLE whatsapp_templates (
    template_id            BIGSERIAL PRIMARY KEY,
    meta_template_id       VARCHAR(64),
    name                   VARCHAR(512) NOT NULL,
    language               VARCHAR(20) NOT NULL,
    category               VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    status                 VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN',
    components             TEXT,
    parameter_count        INTEGER NOT NULL DEFAULT 0,
    last_synced_at         TIMESTAMP,
    created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_whatsapp_templates_name_language UNIQUE (name, language)
);

CREATE INDEX idx_whatsapp_templates_status ON whatsapp_templates(status);

CREATE TABLE whatsapp_inbound_messages (
    inbound_id             BIGSERIAL PRIMARY KEY,
    provider_message_id    VARCHAR(128) NOT NULL UNIQUE,
    from_number            VARCHAR(20) NOT NULL,
    contact_name           VARCHAR(255),
    message_type           VARCHAR(30),
    body                   TEXT,
    media_id               VARCHAR(128),
    media_mime_type        VARCHAR(100),
    context_message_id     VARCHAR(128),
    received_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    raw_payload            TEXT,
    processed              BOOLEAN NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_whatsapp_inbound_from ON whatsapp_inbound_messages(from_number);
CREATE INDEX idx_whatsapp_inbound_received
    ON whatsapp_inbound_messages(received_at);
CREATE INDEX idx_whatsapp_inbound_processed
    ON whatsapp_inbound_messages(processed);

CREATE TABLE whatsapp_webhook_events (
    event_id               BIGSERIAL PRIMARY KEY,
    event_key              VARCHAR(200) NOT NULL UNIQUE,
    event_type             VARCHAR(30) NOT NULL,
    provider_message_id    VARCHAR(128),
    payload                TEXT,
    processed              BOOLEAN NOT NULL DEFAULT FALSE,
    received_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at           TIMESTAMP,
    created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_whatsapp_webhook_events_processed
    ON whatsapp_webhook_events(processed, received_at);
CREATE INDEX idx_whatsapp_webhook_events_message
    ON whatsapp_webhook_events(provider_message_id);

CREATE TABLE whatsapp_consents (
    consent_id             BIGSERIAL PRIMARY KEY,
    phone_number           VARCHAR(20) NOT NULL UNIQUE,
    status                 VARCHAR(20) NOT NULL DEFAULT 'OPT_IN',
    source                 VARCHAR(50),
    member_id              BIGINT,
    last_inbound_at        TIMESTAMP,
    last_outbound_at       TIMESTAMP,
    opted_in_at            TIMESTAMP,
    opted_out_at           TIMESTAMP,
    created_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_whatsapp_consents_status ON whatsapp_consents(status);
