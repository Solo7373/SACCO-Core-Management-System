-- Using VARCHAR instead of PostgreSQL enums for Hibernate compatibility

CREATE TABLE members (
    member_id       BIGSERIAL PRIMARY KEY,
    membership_no   VARCHAR(32) NOT NULL UNIQUE,
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NOT NULL,
    date_of_birth   DATE,
    phone           VARCHAR(20),
    email           VARCHAR(200),
    address         TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    rejection_reason TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE identity_documents (
    document_id     BIGSERIAL PRIMARY KEY,
    member_id       BIGINT NOT NULL REFERENCES members(member_id) ON DELETE CASCADE,
    document_type   VARCHAR(20) NOT NULL,
    document_number VARCHAR(100) NOT NULL,
    issuer          VARCHAR(200),
    issue_date      DATE,
    expiry_date     DATE,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (document_type, document_number)
);

CREATE TABLE verification_records (
    verification_id BIGSERIAL PRIMARY KEY,
    document_id     BIGINT NOT NULL REFERENCES identity_documents(document_id) ON DELETE CASCADE,
    method          VARCHAR(20) NOT NULL,
    outcome         VARCHAR(20) NOT NULL,
    reviewer_id     BIGINT,
    evidence_reference VARCHAR(500),
    timestamp       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE audit_events (
    event_id        BIGSERIAL PRIMARY KEY,
    actor_id        BIGINT,
    action          VARCHAR(100) NOT NULL,
    entity_type     VARCHAR(100) NOT NULL,
    entity_id       BIGINT NOT NULL,
    reason          TEXT,
    timestamp       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    correlation_id  VARCHAR(64)
);

CREATE INDEX idx_members_phone ON members(phone);
CREATE INDEX idx_members_name ON members(last_name, first_name);
CREATE INDEX idx_members_status ON members(status);
CREATE INDEX idx_identity_documents_member ON identity_documents(member_id);
CREATE INDEX idx_verification_records_document ON verification_records(document_id);
CREATE INDEX idx_audit_events_entity ON audit_events(entity_type, entity_id);
