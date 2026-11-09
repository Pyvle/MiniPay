CREATE TABLE installation_state (
    id BIGINT PRIMARY KEY,
    initialized_at TIMESTAMPTZ NOT NULL,

    CHECK (id = 1)
);