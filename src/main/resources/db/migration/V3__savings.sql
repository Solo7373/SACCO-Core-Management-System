-- Using VARCHAR instead of PostgreSQL enums for Hibernate compatibility

CREATE TABLE savings_products (
    product_id      BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    description     TEXT,
    interest_rate   DECIMAL(5,4) DEFAULT 0,
    min_balance     DECIMAL(15,2) DEFAULT 0,
    currency        VARCHAR(3) NOT NULL DEFAULT 'UGX',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE savings_transactions (
    transaction_id  BIGSERIAL PRIMARY KEY,
    account_id      BIGINT NOT NULL REFERENCES accounts(account_id),
    transaction_type VARCHAR(20) NOT NULL,
    amount          DECIMAL(15,2) NOT NULL CHECK (amount > 0),
    currency        VARCHAR(3) NOT NULL DEFAULT 'UGX',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    external_reference VARCHAR(100),
    value_date      DATE NOT NULL DEFAULT CURRENT_DATE,
    description     TEXT,
    posted_by       BIGINT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_savings_transactions_account ON savings_transactions(account_id);
CREATE INDEX idx_savings_transactions_status ON savings_transactions(status);
CREATE INDEX idx_savings_transactions_date ON savings_transactions(value_date);
