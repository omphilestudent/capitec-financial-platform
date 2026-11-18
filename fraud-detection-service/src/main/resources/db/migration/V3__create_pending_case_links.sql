-- A case-opened event whose fraud record has not been stored yet. It is applied
-- when the fraud record arrives, so the link is not lost when events arrive out of order.
CREATE TABLE pending_case_links (
    fraud_id UUID PRIMARY KEY,
    case_id UUID NOT NULL,
    received_at TIMESTAMP NOT NULL
);
