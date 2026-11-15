-- Using VARCHAR instead of PostgreSQL enums for Hibernate compatibility

CREATE TABLE accounts (
    account_id      BIGSERIAL PRIMARY KEY,
    account_number  VARCHAR(32) NOT NULL UNIQUE,
    account_type    VARCHAR(20) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'UGX',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    branch_id       BIGINT,
    opened_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at       TIMESTAMP,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE account_holders (
    account_id      BIGINT NOT NULL REFERENCES accounts(account_id) ON DELETE CASCADE,
    member_id       BIGINT NOT NULL REFERENCES members(member_id),
    holder_type     VARCHAR(20) NOT NULL DEFAULT 'PRIMARY',
    effective_from  DATE NOT NULL DEFAULT CURRENT_DATE,
    effective_to    DATE,
    approval_reference VARCHAR(200),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id, member_id)
);

CREATE TABLE share_products (
    product_id      BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    min_shares      INTEGER NOT NULL DEFAULT 1 CHECK (min_shares > 0),
    price_per_share DECIMAL(15,2) NOT NULL CHECK (price_per_share >= 0),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE share_transactions (
    transaction_id  BIGSERIAL PRIMARY KEY,
    member_id       BIGINT NOT NULL REFERENCES members(member_id),
    product_id      BIGINT NOT NULL REFERENCES share_products(product_id),
    quantity        INTEGER NOT NULL CHECK (quantity > 0),
    amount          DECIMAL(15,2) NOT NULL CHECK (amount >= 0),
    transaction_type VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reference       VARCHAR(100),
    transaction_date DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_accounts_member ON account_holders(member_id);
CREATE INDEX idx_accounts_status ON accounts(status);
CREATE INDEX idx_accounts_type ON accounts(account_type);
CREATE INDEX idx_share_transactions_member ON share_transactions(member_id);
CREATE INDEX idx_share_transactions_product ON share_transactions(product_id);
CREATE INDEX idx_share_transactions_status ON share_transactions(status);
