CREATE TABLE IF NOT EXISTS projector_checkpoints (
    consumer_group   VARCHAR(100) PRIMARY KEY,
    topic            VARCHAR(200) NOT NULL,
    partition_offsets TEXT NOT NULL DEFAULT '{}'
);
