-- Transactional outbox: events are written here in the same transaction as the
-- business change and published to Kafka by OutboxRelay.
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    message_key VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error VARCHAR(1000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,

    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX idx_outbox_events_status_created
    ON outbox_events(status, created_at);
