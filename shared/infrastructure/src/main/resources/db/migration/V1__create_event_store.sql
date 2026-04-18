CREATE TABLE event_store (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_id    VARCHAR(36)  NOT NULL,
    aggregate_type  VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    event_version   SMALLINT     NOT NULL DEFAULT 1,
    sequence_num    BIGINT       NOT NULL,
    payload         JSONB        NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    recorded_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_aggregate_sequence UNIQUE (aggregate_id, sequence_num)
);

CREATE INDEX idx_event_store_aggregate_id ON event_store (aggregate_id);
CREATE INDEX idx_event_store_aggregate_type ON event_store (aggregate_type);
CREATE INDEX idx_event_store_occurred_at ON event_store (occurred_at);
