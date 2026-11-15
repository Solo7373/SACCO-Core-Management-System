-- ============================================================
-- V10: Fees, taxes, compliance spreadsheets and product inventory
-- ============================================================

CREATE TABLE tax_rates (
    tax_rate_id      BIGSERIAL PRIMARY KEY,
    name             VARCHAR(100) NOT NULL,
    code             VARCHAR(40) NOT NULL UNIQUE,
    category         VARCHAR(30) NOT NULL,
    rate_percent     NUMERIC(8,4) NOT NULL,
    applies_to_fees  BOOLEAN NOT NULL DEFAULT TRUE,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    description      VARCHAR(255),
    effective_from   DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_tax_rates_category ON tax_rates(category, is_active);

CREATE TABLE fee_configs (
    fee_config_id     BIGSERIAL PRIMARY KEY,
    name              VARCHAR(120) NOT NULL,
    transaction_type  VARCHAR(30) NOT NULL,
    channel           VARCHAR(30) NOT NULL,
    fee_type          VARCHAR(20) NOT NULL,
    fee_value         NUMERIC(15,4) NOT NULL,
    min_amount        NUMERIC(15,2),
    max_fee           NUMERIC(15,2),
    tax_rate_id       BIGINT REFERENCES tax_rates(tax_rate_id),
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    description       VARCHAR(255),
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_fee_configs_lookup ON fee_configs(transaction_type, channel, is_active);

CREATE TABLE transaction_charges (
    charge_id         BIGSERIAL PRIMARY KEY,
    reference         VARCHAR(40) NOT NULL,
    transaction_type  VARCHAR(30) NOT NULL,
    channel           VARCHAR(30) NOT NULL,
    member_id         BIGINT,
    account_id        BIGINT,
    gross_amount      NUMERIC(15,2) NOT NULL,
    fee_amount        NUMERIC(15,2) NOT NULL DEFAULT 0,
    tax_amount        NUMERIC(15,2) NOT NULL DEFAULT 0,
    net_amount        NUMERIC(15,2) NOT NULL,
    fee_config_id     BIGINT,
    tax_rate_id       BIGINT,
    tax_rate_percent  NUMERIC(8,4),
    description       VARCHAR(255),
    posted_by         BIGINT,
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_charges_reference ON transaction_charges(reference);
CREATE INDEX idx_charges_created ON transaction_charges(created_at);
CREATE INDEX idx_charges_type_channel ON transaction_charges(transaction_type, channel);

ALTER TABLE savings_transactions
    ADD COLUMN channel VARCHAR(30) NOT NULL DEFAULT 'CASH',
    ADD COLUMN fee_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN tax_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD COLUMN net_amount NUMERIC(15,2);

UPDATE savings_transactions SET net_amount = amount WHERE net_amount IS NULL;
ALTER TABLE savings_transactions ALTER COLUMN net_amount SET NOT NULL;

-- ------------------------------------------------------------
-- Product catalogue and imports
-- ------------------------------------------------------------

CREATE TABLE products (
    product_id       BIGSERIAL PRIMARY KEY,
    code             VARCHAR(40) NOT NULL UNIQUE,
    name             VARCHAR(150) NOT NULL,
    category         VARCHAR(30) NOT NULL,
    unit_price       NUMERIC(15,2) NOT NULL,
    cost_price       NUMERIC(15,2),
    stock_quantity   INTEGER NOT NULL DEFAULT 0,
    reorder_level    INTEGER NOT NULL DEFAULT 5,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,
    description      VARCHAR(255),
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE shipping_methods (
    method_id        BIGSERIAL PRIMARY KEY,
    code             VARCHAR(30) NOT NULL UNIQUE,
    name             VARCHAR(100) NOT NULL,
    base_fee         NUMERIC(15,2) NOT NULL,
    estimated_days   INTEGER,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE product_imports (
    import_id            BIGSERIAL PRIMARY KEY,
    product_id           BIGINT NOT NULL REFERENCES products(product_id),
    supplier_name        VARCHAR(200) NOT NULL,
    supplier_country     VARCHAR(100),
    quantity             INTEGER NOT NULL,
    unit_cost            NUMERIC(15,2) NOT NULL,
    shipping_method      VARCHAR(30) NOT NULL,
    shipping_fee         NUMERIC(15,2) NOT NULL DEFAULT 0,
    customs_duty_rate    NUMERIC(8,4) NOT NULL DEFAULT 0,
    customs_duty_amount  NUMERIC(15,2) NOT NULL DEFAULT 0,
    vat_rate             NUMERIC(8,4) NOT NULL DEFAULT 0,
    vat_amount           NUMERIC(15,2) NOT NULL DEFAULT 0,
    other_taxes          NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_landed_cost    NUMERIC(15,2) NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'ORDERED',
    order_date           DATE,
    expected_date        DATE,
    received_date        DATE,
    notes                VARCHAR(500),
    created_by           BIGINT,
    created_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_imports_product ON product_imports(product_id);
CREATE INDEX idx_imports_status ON product_imports(status);

-- ------------------------------------------------------------
-- Seed tax rates
-- ------------------------------------------------------------
INSERT INTO tax_rates (name, code, category, rate_percent, applies_to_fees, description) VALUES
    ('VAT on Financial Fees', 'VAT_FEES', 'GLOBAL', 18.0000, TRUE, 'Standard VAT applied to bank and mobile money charges'),
    ('Mobile Money Levy', 'MOBILE_LEVY', 'MOBILE_MONEY', 0.5000, TRUE, 'Levy on mobile money collection and payout charges'),
    ('Customs Duty', 'CUSTOMS_DUTY', 'IMPORT', 25.0000, FALSE, 'Default customs duty on imported goods'),
    ('Import VAT', 'IMPORT_VAT', 'IMPORT', 18.0000, FALSE, 'VAT charged on imported goods and landed cost'),
    ('Withholding Tax on Interest', 'WHT_INTEREST', 'SAVINGS', 15.0000, FALSE, 'Withholding tax on interest earned by members');

-- ------------------------------------------------------------
-- Seed fee configurations
-- ------------------------------------------------------------
INSERT INTO fee_configs (name, transaction_type, channel, fee_type, fee_value, tax_rate_id, is_active, description) VALUES
    ('Mobile Money Collection Charge', 'DEPOSIT', 'MOBILE_MONEY', 'PERCENTAGE', 1.0000,
        (SELECT tax_rate_id FROM tax_rates WHERE code = 'VAT_FEES'), TRUE, '1% charge on mobile money collections'),
    ('Mobile Money Payout Charge', 'WITHDRAWAL', 'MOBILE_MONEY', 'PERCENTAGE', 1.0000,
        (SELECT tax_rate_id FROM tax_rates WHERE code = 'VAT_FEES'), TRUE, '1% charge on mobile money payouts'),
    ('Cross-Bank Transfer Charge', 'TRANSFER', 'CROSS_BANK', 'FIXED', 2500.0000,
        (SELECT tax_rate_id FROM tax_rates WHERE code = 'VAT_FEES'), TRUE, 'Flat UGX 2,500 charge for inter-bank transfers'),
    ('Bank Deposit Charge', 'DEPOSIT', 'BANK', 'FIXED', 500.0000,
        (SELECT tax_rate_id FROM tax_rates WHERE code = 'VAT_FEES'), TRUE, 'Flat UGX 500 charge for bank deposits'),
    ('Bank Withdrawal Charge', 'WITHDRAWAL', 'BANK', 'FIXED', 500.0000,
        (SELECT tax_rate_id FROM tax_rates WHERE code = 'VAT_FEES'), TRUE, 'Flat UGX 500 charge for bank withdrawals');

-- ------------------------------------------------------------
-- Seed shipping methods (import dropdown)
-- ------------------------------------------------------------
INSERT INTO shipping_methods (code, name, base_fee, estimated_days) VALUES
    ('SEA_FREIGHT', 'Sea Freight (Consolidated)', 2500000.00, 45),
    ('AIR_FREIGHT', 'Air Freight', 8000000.00, 10),
    ('COURIER', 'International Courier', 1500000.00, 5),
    ('ROAD', 'Cross-Border Road Freight', 800000.00, 14);

-- ------------------------------------------------------------
-- Seed product catalogue
-- ------------------------------------------------------------
INSERT INTO products (code, name, category, unit_price, cost_price, stock_quantity, reorder_level, description) VALUES
    ('PB-001', 'Member Passbook', 'PASSBOOK', 5000.00, 3000.00, 250, 50, 'Standard SACCO member passbook'),
    ('SB-001', 'Statement Book', 'STATEMENT_BOOK', 3000.00, 1800.00, 300, 60, 'Printed account statement booklet'),
    ('TS-001', 'Branded T-Shirt', 'MERCHANDISE', 25000.00, 15000.00, 80, 20, 'SACCO branded staff and member t-shirt'),
    ('MG-001', 'Branded Coffee Mug', 'MERCHANDISE', 15000.00, 8000.00, 120, 25, 'SACCO branded ceramic mug'),
    ('ID-001', 'Member ID Card', 'STATIONERY', 10000.00, 6000.00, 400, 100, 'Printed member identification card'),
    ('RR-001', 'Receipt Roll', 'STATIONERY', 2000.00, 1200.00, 500, 100, 'Thermal receipt roll for teller printers'),
    ('EQ-001', 'Office Chair', 'EQUIPMENT', 250000.00, 180000.00, 12, 3, 'Staff office chair'),
    ('EQ-002', 'Counterfeit Detector', 'EQUIPMENT', 180000.00, 120000.00, 6, 2, 'UV and magnetic note checker');
