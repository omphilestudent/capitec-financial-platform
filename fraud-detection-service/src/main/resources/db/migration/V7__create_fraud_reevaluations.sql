ALTER TABLE fraud_detections ADD COLUMN superseded_at TIMESTAMP;
ALTER TABLE fraud_detections ADD COLUMN superseded_by_revision UUID;

CREATE TABLE fraud_reevaluations (
    id UUID PRIMARY KEY,
    detection_id UUID NOT NULL REFERENCES fraud_detections(id),
    transaction_id UUID NOT NULL,
    account_id UUID NOT NULL,
    original_outcome VARCHAR(30) NOT NULL,
    revised_outcome VARCHAR(30) NOT NULL,
    prior_in_window BIGINT NOT NULL,
    window_start TIMESTAMP NOT NULL,
    window_end TIMESTAMP NOT NULL,
    rule_version VARCHAR(60) NOT NULL,
    revised_at TIMESTAMP NOT NULL,

    CONSTRAINT uq_reevaluation_detection_rule UNIQUE (detection_id, rule_version),
    CONSTRAINT chk_reevaluation_outcome CHECK (revised_outcome IN ('DETECTED', 'NOT_DETECTED'))
);

CREATE INDEX idx_reevaluations_transaction ON fraud_reevaluations(transaction_id);
