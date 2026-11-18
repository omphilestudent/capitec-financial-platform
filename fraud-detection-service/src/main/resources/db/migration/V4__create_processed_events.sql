CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    transaction_id UUID NOT NULL,
    processed_at TIMESTAMP NOT NULL
);
