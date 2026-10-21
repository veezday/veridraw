CREATE TABLE IF NOT EXISTS inbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    published_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_inbox_events_published ON inbox_events(published) WHERE published = FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS idx_inbox_events_aggregate_unique ON inbox_events(aggregate_type, aggregate_id);
CREATE INDEX IF NOT EXISTS idx_inbox_events_event_type ON inbox_events(event_type);
CREATE INDEX IF NOT EXISTS idx_inbox_events_created_at ON inbox_events(created_at);