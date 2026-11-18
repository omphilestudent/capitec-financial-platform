CREATE TABLE fraud_evaluation_publications (
    transaction_id UUID PRIMARY KEY,
    outcome VARCHAR(30) NOT NULL,
    published_at TIMESTAMP NOT NULL
);

INSERT INTO fraud_evaluation_publications (transaction_id, outcome, published_at)
SELECT transaction_id, outcome, processed_at FROM processed_transactions
ON CONFLICT (transaction_id) DO NOTHING;
