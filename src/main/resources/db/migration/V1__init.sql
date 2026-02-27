-- Inbox table for consumer-side idempotency + controlled retries.
-- One row per eventId. 'DONE' means we already sent the email.

CREATE TABLE IF NOT EXISTS notification_inbox (
                                event_id UUID PRIMARY KEY,
                                status  VARCHAR(20) NOT NULL,   -- PROCESSING | DONE
                                attempts INT NOT NULL DEFAULT 0, -- how many times we've tried to process this event
                                updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );

-- Optional: help ops/debugging queries (who is stuck in PROCESSING, etc.)
CREATE INDEX IF NOT EXISTS idx_notification_inbox_status
    ON notification_inbox(status);

-- Optional but useful for ops: find oldest stuck rows faster
CREATE INDEX IF NOT EXISTS idx_notification_inbox_status_updated_at
    ON notification_inbox(status, updated_at);