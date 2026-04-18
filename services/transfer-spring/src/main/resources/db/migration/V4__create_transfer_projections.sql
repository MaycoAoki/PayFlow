CREATE TABLE transfer_projections (
    transfer_id       VARCHAR(36)    PRIMARY KEY,
    source_account_id VARCHAR(36)    NOT NULL,
    target_account_id VARCHAR(36)    NOT NULL,
    amount            NUMERIC(19,4)  NOT NULL,
    currency          VARCHAR(3)     NOT NULL,
    status            VARCHAR(20)    NOT NULL,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transfer_projections_status ON transfer_projections (status);
CREATE INDEX idx_transfer_projections_source_account ON transfer_projections (source_account_id);
CREATE INDEX idx_transfer_projections_target_account ON transfer_projections (target_account_id);
