CREATE TABLE idempotency_records (
    idempotency_key UUID PRIMARY KEY,
    operation_type VARCHAR(20) NOT NULL,
    wallet_id BIGINT NOT NULL,
    to_wallet_id BIGINT,
    amount DECIMAL(19, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_status INTEGER,
    response_body TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_type
        CHECK (operation_type IN ('DEPOSIT', 'TRANSFER', 'WITHDRAWAL')),

    CONSTRAINT chk_amount_non_negative
        CHECK (amount > 0),

    CONSTRAINT chk_status
        CHECK (
            (status = 'PROCESSING'
                AND response_status IS NULL
                AND response_body IS NULL)
            OR
            (status = 'COMPLETED'
                AND response_status IS NOT NULL
                AND response_body IS NOT NULL)
        )   
);