CREATE TABLE aggregated_transactions (
    id UUID PRIMARY KEY,
    source VARCHAR(40) NOT NULL,
    external_id VARCHAR(120) NOT NULL,
    fingerprint CHAR(64) NOT NULL,
    customer_id UUID NOT NULL,
    account_id UUID NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    category VARCHAR(40) NOT NULL,
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    counted BOOLEAN NOT NULL,
    duplicate_of UUID REFERENCES aggregated_transactions(id),
    imported_at TIMESTAMP NOT NULL,

    CONSTRAINT uq_aggregated_source_external UNIQUE (source, external_id),
    CONSTRAINT chk_aggregated_direction CHECK (direction IN ('CREDIT', 'DEBIT')),
    CONSTRAINT chk_aggregated_amount CHECK (amount > 0),
    CONSTRAINT chk_aggregated_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_aggregated_account_time ON aggregated_transactions(account_id, occurred_at);
CREATE INDEX idx_aggregated_customer_time ON aggregated_transactions(customer_id, occurred_at);
CREATE INDEX idx_aggregated_category ON aggregated_transactions(category);
CREATE INDEX idx_aggregated_fingerprint ON aggregated_transactions(fingerprint);

CREATE TABLE daily_account_summaries (
    account_id UUID NOT NULL,
    summary_date DATE NOT NULL,
    customer_id UUID NOT NULL,
    credit_count BIGINT NOT NULL DEFAULT 0,
    debit_count BIGINT NOT NULL DEFAULT 0,
    credit_total NUMERIC(19, 2) NOT NULL DEFAULT 0,
    debit_total NUMERIC(19, 2) NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id, summary_date)
);

CREATE INDEX idx_daily_summaries_customer ON daily_account_summaries(customer_id, summary_date);

CREATE TABLE processed_inbound_events (
    event_id UUID PRIMARY KEY,
    source VARCHAR(40) NOT NULL,
    processed_at TIMESTAMP NOT NULL
);
