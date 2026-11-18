CREATE TABLE statement_ledger_entries (
    aggregated_transaction_id UUID PRIMARY KEY,
    source VARCHAR(40) NOT NULL,
    external_id VARCHAR(120) NOT NULL,
    fraud_key UUID NOT NULL,
    customer_id UUID NOT NULL,
    account_id UUID NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    category VARCHAR(40) NOT NULL,
    description VARCHAR(500),
    occurred_at TIMESTAMP NOT NULL,
    received_at TIMESTAMP NOT NULL,

    CONSTRAINT uq_ledger_source_external UNIQUE (source, external_id),
    CONSTRAINT chk_ledger_direction CHECK (direction IN ('CREDIT', 'DEBIT')),
    CONSTRAINT chk_ledger_amount CHECK (amount > 0)
);

CREATE INDEX idx_ledger_account_time ON statement_ledger_entries(account_id, occurred_at);
CREATE INDEX idx_ledger_customer ON statement_ledger_entries(customer_id);
CREATE INDEX idx_ledger_fraud_key ON statement_ledger_entries(fraud_key);

CREATE TABLE statement_fraud_flags (
    transaction_key UUID PRIMARY KEY,
    fraud_id UUID NOT NULL UNIQUE,
    fraud_reference VARCHAR(40),
    customer_id UUID NOT NULL,
    account_id UUID NOT NULL,
    risk_level VARCHAR(10),
    received_at TIMESTAMP NOT NULL
);

CREATE TABLE statement_fraud_decisions (
    transaction_key UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    outcome VARCHAR(30) NOT NULL,
    received_at TIMESTAMP NOT NULL,

    CONSTRAINT chk_decision_outcome CHECK (outcome IN ('DETECTED', 'NOT_DETECTED', 'ACCOUNT_NOT_FOUND'))
);

CREATE TABLE statement_inbound_events (
    event_id UUID PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    received_at TIMESTAMP NOT NULL
);

ALTER TABLE statements DROP CONSTRAINT chk_statement_status;
ALTER TABLE statements ADD CONSTRAINT chk_statement_status CHECK (status IN ('GENERATED', 'FAILED', 'WITHDRAWN'));
ALTER TABLE statements ADD COLUMN eligible_transaction_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE statements ADD COLUMN excluded_transaction_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE statement_access_logs DROP CONSTRAINT chk_access_type;
ALTER TABLE statement_access_logs ADD CONSTRAINT chk_access_type CHECK (access_type IN (
    'GENERATED', 'LIST', 'VIEW', 'LINK_CREATED', 'LINK_REVOKED', 'DOWNLOAD_ATTEMPT', 'DOWNLOADED', 'WITHDRAWN'));
