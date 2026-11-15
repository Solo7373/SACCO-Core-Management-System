-- Collateral / asset register
-- Assets are held by the SACCO against member loans. Status changes and release
-- authorizations are retained in a separate history table for audit and legal safety.

CREATE TABLE collateral_assets (
    collateral_id           BIGSERIAL PRIMARY KEY,
    asset_code              VARCHAR(32) NOT NULL UNIQUE,
    member_id               BIGINT NOT NULL REFERENCES members(member_id),
    loan_account_id         BIGINT REFERENCES accounts(account_id) ON DELETE SET NULL,
    asset_type              VARCHAR(40) NOT NULL,
    title_number            VARCHAR(200) NOT NULL,
    description             TEXT,
    estimated_value         DECIMAL(15,2) NOT NULL CHECK (estimated_value >= 0),
    forced_sale_value       DECIMAL(15,2) CHECK (forced_sale_value >= 0),
    valuation_date          DATE,
    valuer_name             VARCHAR(200),
    registered_owner_name   VARCHAR(200),
    custodian_location      VARCHAR(300),
    status                  VARCHAR(30) NOT NULL DEFAULT 'HELD_IN_CUSTODY',
    registered_by           BIGINT,
    registered_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_to             VARCHAR(200),
    release_date            DATE,
    release_authorized_by   VARCHAR(200),
    release_authorized_at   TIMESTAMP,
    release_signature       VARCHAR(500),
    release_notes           TEXT,
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (asset_type, title_number)
);

CREATE TABLE collateral_documents (
    document_id             BIGSERIAL PRIMARY KEY,
    collateral_id           BIGINT NOT NULL REFERENCES collateral_assets(collateral_id) ON DELETE CASCADE,
    document_type           VARCHAR(40) NOT NULL,
    document_name           VARCHAR(255) NOT NULL,
    file_path               VARCHAR(500),
    file_name               VARCHAR(255),
    file_type               VARCHAR(100),
    file_size               BIGINT,
    uploaded_by             BIGINT,
    uploaded_at             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE collateral_status_history (
    history_id              BIGSERIAL PRIMARY KEY,
    collateral_id           BIGINT NOT NULL REFERENCES collateral_assets(collateral_id) ON DELETE CASCADE,
    from_status             VARCHAR(30),
    to_status               VARCHAR(30) NOT NULL,
    action                  VARCHAR(50) NOT NULL,
    notes                   TEXT,
    actor_id                BIGINT,
    authorized_by           VARCHAR(200),
    signature               VARCHAR(500),
    created_at              TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_collateral_assets_member ON collateral_assets(member_id);
CREATE INDEX idx_collateral_assets_loan ON collateral_assets(loan_account_id);
CREATE INDEX idx_collateral_assets_status ON collateral_assets(status);
CREATE INDEX idx_collateral_assets_type ON collateral_assets(asset_type);
CREATE INDEX idx_collateral_assets_created ON collateral_assets(created_at DESC);
CREATE INDEX idx_collateral_documents_asset ON collateral_documents(collateral_id);
CREATE INDEX idx_collateral_history_asset ON collateral_status_history(collateral_id);
