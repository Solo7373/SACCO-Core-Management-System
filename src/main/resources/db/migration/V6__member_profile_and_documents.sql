-- Member profile enrichment: KYC demographics, next of kin, passport photo
ALTER TABLE members
    ADD COLUMN gender VARCHAR(20),
    ADD COLUMN marital_status VARCHAR(30),
    ADD COLUMN next_of_kin_name VARCHAR(200),
    ADD COLUMN next_of_kin_relationship VARCHAR(100),
    ADD COLUMN next_of_kin_phone VARCHAR(20),
    ADD COLUMN next_of_kin_address VARCHAR(500),
    ADD COLUMN passport_photo_path VARCHAR(500);

-- Uploaded scans/photos attached to identity documents
ALTER TABLE identity_documents
    ADD COLUMN file_path VARCHAR(500),
    ADD COLUMN file_name VARCHAR(255),
    ADD COLUMN file_type VARCHAR(100),
    ADD COLUMN file_size BIGINT;

CREATE INDEX idx_identity_documents_verification_status ON identity_documents(status);
CREATE INDEX idx_savings_transactions_created_at ON savings_transactions(created_at);
CREATE INDEX idx_audit_events_timestamp ON audit_events(timestamp DESC);
