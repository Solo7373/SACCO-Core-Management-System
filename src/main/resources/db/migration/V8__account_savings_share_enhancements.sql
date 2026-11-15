-- Structured number generation for accounts and other financial references.
CREATE TABLE number_sequences (
    sequence_name   VARCHAR(40) NOT NULL,
    year            INTEGER NOT NULL,
    last_number     INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (sequence_name, year)
);

-- Account lifecycle and fixed-deposit details.
ALTER TABLE accounts ADD COLUMN last_activity_at TIMESTAMP;
ALTER TABLE accounts ADD COLUMN dormant_since TIMESTAMP;
ALTER TABLE accounts ADD COLUMN fixed_deposit_maturity_date DATE;
ALTER TABLE accounts ADD COLUMN fixed_deposit_interest_rate DECIMAL(5,2);
ALTER TABLE accounts ADD COLUMN fixed_deposit_term_months INTEGER;

UPDATE accounts
SET last_activity_at = COALESCE(closed_at, opened_at, created_at);

-- Savings receipts and supervisor approvals.
ALTER TABLE savings_transactions ADD COLUMN reference VARCHAR(40);
UPDATE savings_transactions
SET reference = 'TXN-LEGACY-' || transaction_id
WHERE reference IS NULL;
ALTER TABLE savings_transactions ALTER COLUMN reference SET NOT NULL;
CREATE UNIQUE INDEX idx_savings_transactions_reference ON savings_transactions(reference);
ALTER TABLE savings_transactions ADD COLUMN authorized_by VARCHAR(200);
ALTER TABLE savings_transactions ADD COLUMN approval_reference VARCHAR(100);

-- Share transaction pricing and approval metadata.
ALTER TABLE share_transactions ADD COLUMN unit_price DECIMAL(15,2);
UPDATE share_transactions st
SET unit_price = sp.price_per_share
FROM share_products sp
WHERE st.product_id = sp.product_id;
ALTER TABLE share_transactions ALTER COLUMN unit_price SET NOT NULL;
ALTER TABLE share_transactions ADD COLUMN processed_by BIGINT;
ALTER TABLE share_transactions ADD COLUMN processed_at TIMESTAMP;
