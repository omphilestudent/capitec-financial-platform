-- Notifications created from real business events. source_event_id is unique, so a
-- redelivered event cannot create a second notification.
CREATE TABLE notifications (
    id UUID PRIMARY KEY,
    source_event_id UUID NOT NULL UNIQUE,
    notification_type VARCHAR(40) NOT NULL,
    severity VARCHAR(10) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    case_id UUID,
    fraud_id UUID,
    customer_id UUID,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT chk_notification_severity CHECK (severity IN ('INFO','WARNING','CRITICAL'))
);

CREATE INDEX idx_notifications_created ON notifications(created_at);
CREATE INDEX idx_notifications_read ON notifications(read_at);
