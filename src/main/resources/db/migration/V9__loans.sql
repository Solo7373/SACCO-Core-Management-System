-- Loan products, applications, guarantors, amortization schedules and repayments.

CREATE TABLE loan_products (
    product_id          BIGSERIAL PRIMARY KEY,
    name                VARCHAR(100) NOT NULL UNIQUE,
    description         TEXT,
    interest_rate       DECIMAL(6,3) NOT NULL,
    interest_method     VARCHAR(30) NOT NULL DEFAULT 'REDUCING_BALANCE',
    max_term_months     INTEGER NOT NULL DEFAULT 12,
    min_amount          DECIMAL(15,2) NOT NULL DEFAULT 0,
    max_amount          DECIMAL(15,2),
    max_multiplier      DECIMAL(6,2) NOT NULL DEFAULT 3.00,
    penalty_rate        DECIMAL(6,3) NOT NULL DEFAULT 0,
    requires_guarantor  BOOLEAN NOT NULL DEFAULT FALSE,
    status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE loans (
    loan_id                  BIGSERIAL PRIMARY KEY,
    loan_number              VARCHAR(32) NOT NULL UNIQUE,
    member_id                BIGINT NOT NULL REFERENCES members(member_id),
    product_id               BIGINT NOT NULL REFERENCES loan_products(product_id),
    amount                   DECIMAL(15,2) NOT NULL,
    interest_rate            DECIMAL(6,3) NOT NULL,
    interest_method          VARCHAR(30) NOT NULL,
    term_months              INTEGER NOT NULL,
    purpose                  TEXT,
    status                   VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    application_date         DATE NOT NULL DEFAULT CURRENT_DATE,
    eligibility_notes        TEXT,
    reviewed_by              BIGINT,
    reviewed_at              TIMESTAMP,
    approved_by              BIGINT,
    approved_at              TIMESTAMP,
    rejected_by              BIGINT,
    rejected_at              TIMESTAMP,
    rejection_reason         TEXT,
    disbursed_by             BIGINT,
    disbursed_at             TIMESTAMP,
    disbursement_account_id  BIGINT REFERENCES accounts(account_id),
    outstanding_principal    DECIMAL(15,2) NOT NULL DEFAULT 0,
    outstanding_interest     DECIMAL(15,2) NOT NULL DEFAULT 0,
    total_paid               DECIMAL(15,2) NOT NULL DEFAULT 0,
    next_due_date            DATE,
    closed_at                TIMESTAMP,
    created_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE loan_guarantors (
    guarantor_id        BIGSERIAL PRIMARY KEY,
    loan_id             BIGINT NOT NULL REFERENCES loans(loan_id) ON DELETE CASCADE,
    member_id           BIGINT NOT NULL REFERENCES members(member_id),
    guaranteed_amount   DECIMAL(15,2) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PLEDGED',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE loan_schedules (
    schedule_id      BIGSERIAL PRIMARY KEY,
    loan_id          BIGINT NOT NULL REFERENCES loans(loan_id) ON DELETE CASCADE,
    installment_no   INTEGER NOT NULL,
    due_date         DATE NOT NULL,
    principal_due    DECIMAL(15,2) NOT NULL,
    interest_due     DECIMAL(15,2) NOT NULL,
    total_due        DECIMAL(15,2) NOT NULL,
    principal_paid   DECIMAL(15,2) NOT NULL DEFAULT 0,
    interest_paid    DECIMAL(15,2) NOT NULL DEFAULT 0,
    penalty_due      DECIMAL(15,2) NOT NULL DEFAULT 0,
    penalty_paid     DECIMAL(15,2) NOT NULL DEFAULT 0,
    penalty_charged_until DATE,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    UNIQUE (loan_id, installment_no)
);

CREATE TABLE loan_repayments (
    repayment_id        BIGSERIAL PRIMARY KEY,
    loan_id             BIGINT NOT NULL REFERENCES loans(loan_id),
    reference           VARCHAR(40) NOT NULL UNIQUE,
    amount              DECIMAL(15,2) NOT NULL,
    principal_portion   DECIMAL(15,2) NOT NULL DEFAULT 0,
    interest_portion    DECIMAL(15,2) NOT NULL DEFAULT 0,
    penalty_portion     DECIMAL(15,2) NOT NULL DEFAULT 0,
    payment_date        DATE NOT NULL DEFAULT CURRENT_DATE,
    notes               TEXT,
    posted_by           BIGINT,
    status              VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_loans_member ON loans(member_id);
CREATE INDEX idx_loans_status ON loans(status);
CREATE INDEX idx_loan_guarantors_loan ON loan_guarantors(loan_id);
CREATE INDEX idx_loan_schedules_loan ON loan_schedules(loan_id);
CREATE INDEX idx_loan_repayments_loan ON loan_repayments(loan_id);

INSERT INTO loan_products
    (name, description, interest_rate, interest_method, max_term_months, min_amount, max_amount, max_multiplier, penalty_rate, requires_guarantor)
VALUES
    ('Emergency Loan', 'Short-term loan for urgent member needs', 12.000, 'REDUCING_BALANCE', 12, 50000, 2000000, 2.00, 2.000, FALSE),
    ('Development Loan', 'Investment and business development loan', 15.000, 'REDUCING_BALANCE', 36, 100000, 20000000, 3.00, 2.000, TRUE),
    ('School Fees Loan', 'Seasonal school fees financing', 10.000, 'FLAT', 12, 50000, 5000000, 2.50, 1.500, TRUE)
ON CONFLICT (name) DO NOTHING;
