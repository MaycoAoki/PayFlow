CREATE TABLE account_projections (
    account_id  VARCHAR(36)   PRIMARY KEY,
    owner_id    VARCHAR(255)  NOT NULL,
    balance     NUMERIC(19,4) NOT NULL,
    currency    VARCHAR(3)    NOT NULL,
    status      VARCHAR(20)   NOT NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE transaction_history (
    id          BIGSERIAL     PRIMARY KEY,
    account_id  VARCHAR(36)   NOT NULL,
    event_type  VARCHAR(100)  NOT NULL,
    amount      NUMERIC(19,4),
    currency    VARCHAR(3),
    transfer_id VARCHAR(36),
    occurred_at TIMESTAMPTZ   NOT NULL,
    recorded_at TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_transaction_history_account_id ON transaction_history (account_id);
CREATE INDEX idx_transaction_history_occurred_at ON transaction_history (occurred_at);
