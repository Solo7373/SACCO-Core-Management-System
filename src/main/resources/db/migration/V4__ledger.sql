-- Using VARCHAR instead of PostgreSQL enums for Hibernate compatibility

CREATE TABLE chart_of_accounts (
    account_code    VARCHAR(20) PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    category        VARCHAR(20) NOT NULL,
    parent_code     VARCHAR(20) REFERENCES chart_of_accounts(account_code),
    is_bank_account BOOLEAN NOT NULL DEFAULT FALSE,
    is_cash_account BOOLEAN NOT NULL DEFAULT FALSE,
    currency        VARCHAR(3) NOT NULL DEFAULT 'UGX',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE journal_entries (
    journal_id      BIGSERIAL PRIMARY KEY,
    entry_date      DATE NOT NULL DEFAULT CURRENT_DATE,
    description     TEXT NOT NULL,
    source_type     VARCHAR(50) NOT NULL,
    source_id       BIGINT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    total_debit     DECIMAL(15,2) NOT NULL,
    total_credit    DECIMAL(15,2) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'UGX',
    posted_by       BIGINT,
    posted_at       TIMESTAMP,
    reversed_by     BIGINT,
    reversed_at     TIMESTAMP,
    reversal_reason TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE journal_lines (
    line_id         BIGSERIAL PRIMARY KEY,
    journal_id      BIGINT NOT NULL REFERENCES journal_entries(journal_id) ON DELETE CASCADE,
    account_code    VARCHAR(20) NOT NULL REFERENCES chart_of_accounts(account_code),
    debit           DECIMAL(15,2) NOT NULL DEFAULT 0,
    credit          DECIMAL(15,2) NOT NULL DEFAULT 0,
    description     TEXT,
    CHECK (debit >= 0),
    CHECK (credit >= 0),
    CHECK (debit = 0 OR credit = 0)
);

INSERT INTO chart_of_accounts (account_code, name, category, is_bank_account, is_cash_account) VALUES
('1000', 'Cash on Hand', 'ASSET', FALSE, TRUE),
('1010', 'Bank Account', 'ASSET', TRUE, FALSE),
('1020', 'Petty Cash', 'ASSET', FALSE, TRUE),
('1100', 'Member Savings', 'ASSET', FALSE, FALSE),
('1110', 'Member Shares', 'ASSET', FALSE, FALSE),
('1200', 'Loans Receivable', 'ASSET', FALSE, FALSE),
('1210', 'Interest Receivable', 'ASSET', FALSE, FALSE),
('1300', 'Fixed Assets', 'ASSET', FALSE, FALSE),
('2000', 'Member Savings Liability', 'LIABILITY', FALSE, FALSE),
('2100', 'Member Shares Liability', 'LIABILITY', FALSE, FALSE),
('2200', 'Interest Payable', 'LIABILITY', FALSE, FALSE),
('2300', 'Accounts Payable', 'LIABILITY', FALSE, FALSE),
('2400', 'External Loans', 'LIABILITY', FALSE, FALSE),
('3000', 'Share Capital', 'EQUITY', FALSE, FALSE),
('3100', 'Retained Earnings', 'EQUITY', FALSE, FALSE),
('3200', 'Reserve Fund', 'EQUITY', FALSE, FALSE),
('4000', 'Interest Income', 'INCOME', FALSE, FALSE),
('4100', 'Fee Income', 'INCOME', FALSE, FALSE),
('4200', 'Penalty Income', 'INCOME', FALSE, FALSE),
('4300', 'Other Income', 'INCOME', FALSE, FALSE),
('5000', 'Interest Expense', 'EXPENSE', FALSE, FALSE),
('5100', 'Administrative Expenses', 'EXPENSE', FALSE, FALSE),
('5200', 'Staff Costs', 'EXPENSE', FALSE, FALSE),
('5300', 'Write-offs', 'EXPENSE', FALSE, FALSE),
('5400', 'Other Expenses', 'EXPENSE', FALSE, FALSE);

CREATE INDEX idx_journal_entries_status ON journal_entries(status);
CREATE INDEX idx_journal_entries_source ON journal_entries(source_type, source_id);
CREATE INDEX idx_journal_entries_date ON journal_entries(entry_date);
CREATE INDEX idx_journal_lines_journal ON journal_lines(journal_id);
CREATE INDEX idx_journal_lines_account ON journal_lines(account_code);
