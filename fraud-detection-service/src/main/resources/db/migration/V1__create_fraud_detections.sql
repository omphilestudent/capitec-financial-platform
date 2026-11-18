-- Transactions this service has evaluated. Used for the velocity rule and to
-- make transaction-events processing idempotent.
CREATE TABLE processed_transactions (
    transaction_id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    transaction_created_at TIMESTAMP NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    outcome VARCHAR(30) NOT NULL
);

CREATE INDEX idx_processed_tx_account_time ON processed_transactions(account_id, transaction_created_at);

CREATE SEQUENCE fraud_reference_seq START WITH 100000;

-- A detection record. One per transaction: the primary rule plus the explanation of every rule that fired.
CREATE TABLE fraud_detections (
    id UUID PRIMARY KEY,
    fraud_reference VARCHAR(20) NOT NULL UNIQUE,
    case_id UUID,
    customer_id UUID,
    account_id UUID NOT NULL,
    transaction_id UUID NOT NULL UNIQUE,
    fraud_type VARCHAR(40) NOT NULL,
    fraud_category VARCHAR(40) NOT NULL,
    risk_score INTEGER NOT NULL,
    risk_level VARCHAR(10) NOT NULL,
    detection_rule VARCHAR(40) NOT NULL,
    detection_description TEXT NOT NULL,
    detection_source VARCHAR(40) NOT NULL,
    investigation_status VARCHAR(20) NOT NULL,
    detected_at TIMESTAMP NOT NULL,
    last_updated TIMESTAMP NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_fraud_risk_score CHECK (risk_score BETWEEN 0 AND 100),
    CONSTRAINT chk_fraud_risk_level CHECK (risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL')),
    CONSTRAINT chk_fraud_status CHECK (investigation_status IN ('OPEN','UNDER_REVIEW','CONFIRMED_FRAUD','FALSE_POSITIVE'))
);

CREATE INDEX idx_fraud_case ON fraud_detections(case_id);
CREATE INDEX idx_fraud_customer ON fraud_detections(customer_id);
CREATE INDEX idx_fraud_risk ON fraud_detections(risk_level);
CREATE INDEX idx_fraud_status ON fraud_detections(investigation_status);
CREATE INDEX idx_fraud_detected ON fraud_detections(detected_at);
