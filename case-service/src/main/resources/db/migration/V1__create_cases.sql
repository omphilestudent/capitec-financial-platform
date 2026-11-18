-- Case management. Case numbers come from a sequence so they are unique and ordered.
CREATE SEQUENCE case_number_seq START WITH 100000;

CREATE TABLE cases (
    id UUID PRIMARY KEY,
    case_number VARCHAR(20) NOT NULL UNIQUE,
    subject VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL,
    priority VARCHAR(10) NOT NULL,
    origin VARCHAR(30) NOT NULL,
    case_type VARCHAR(40) NOT NULL,
    customer_id UUID,
    account_id UUID,
    transaction_id UUID,
    -- Set when the case was opened from a fraud alert. Unique, so a duplicate
    -- fraud event cannot open a second case.
    source_fraud_id UUID UNIQUE,
    assigned_investigator VARCHAR(100),
    owner VARCHAR(100),
    resolution_details TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    closed_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_case_status CHECK (status IN ('NEW','OPEN','IN_PROGRESS','PENDING','ESCALATED','RESOLVED','CLOSED')),
    CONSTRAINT chk_case_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT chk_case_origin CHECK (origin IN ('MANUAL','FRAUD_DETECTION')),
    CONSTRAINT chk_case_type CHECK (case_type IN ('FRAUD_INVESTIGATION','GENERAL_INQUIRY'))
);

CREATE INDEX idx_cases_status ON cases(status);
CREATE INDEX idx_cases_priority ON cases(priority);
CREATE INDEX idx_cases_customer ON cases(customer_id);
CREATE INDEX idx_cases_created ON cases(created_at);

-- Append-only history of everything that happened to a case.
CREATE TABLE case_activity (
    id UUID PRIMARY KEY,
    case_id UUID NOT NULL REFERENCES cases(id),
    activity_type VARCHAR(40) NOT NULL,
    description TEXT NOT NULL,
    actor VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_case_activity_case ON case_activity(case_id, created_at);
