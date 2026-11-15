-- Loan collections: formal demand notices and the collection-action trail.
-- Demand rows snapshot the arrears at the moment the notice is issued so a
-- later repayment or penalty accrual never rewrites the historical letter.

CREATE TABLE loan_demands (
    demand_id           BIGSERIAL PRIMARY KEY,
    demand_number       VARCHAR(40) NOT NULL UNIQUE,
    loan_id             BIGINT NOT NULL REFERENCES loans(loan_id),
    stage               VARCHAR(30) NOT NULL,
    amount_demanded     DECIMAL(15,2) NOT NULL,
    principal_arrears   DECIMAL(15,2) NOT NULL DEFAULT 0,
    interest_arrears    DECIMAL(15,2) NOT NULL DEFAULT 0,
    penalty_arrears     DECIMAL(15,2) NOT NULL DEFAULT 0,
    days_overdue        INTEGER NOT NULL DEFAULT 0,
    issued_by           BIGINT,
    issued_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_date            DATE NOT NULL,
    delivery_method     VARCHAR(30) NOT NULL DEFAULT 'PRINT',
    status              VARCHAR(30) NOT NULL DEFAULT 'ISSUED',
    notes               TEXT,
    document_seal       VARCHAR(64),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_loan_demands_loan ON loan_demands(loan_id);
CREATE INDEX idx_loan_demands_status ON loan_demands(status);
CREATE INDEX idx_loan_demands_issued_at ON loan_demands(issued_at);

CREATE TABLE loan_collection_actions (
    action_id        BIGSERIAL PRIMARY KEY,
    loan_id          BIGINT NOT NULL REFERENCES loans(loan_id),
    demand_id        BIGINT REFERENCES loan_demands(demand_id),
    action_type      VARCHAR(40) NOT NULL,
    outcome          VARCHAR(30) NOT NULL,
    promised_amount  DECIMAL(15,2),
    promised_date    DATE,
    notes            TEXT,
    actor_id         BIGINT,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_loan_collection_actions_loan ON loan_collection_actions(loan_id);
CREATE INDEX idx_loan_collection_actions_demand ON loan_collection_actions(demand_id);
CREATE INDEX idx_loan_collection_actions_promise ON loan_collection_actions(promised_date);
