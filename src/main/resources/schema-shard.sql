CREATE TABLE IF NOT EXISTS payment_entries (
    id UUID PRIMARY KEY,
    bulk_request_id UUID NOT NULL,
    coffee_type VARCHAR(50) NOT NULL,
    price DOUBLE PRECISION NOT NULL,
    currency VARCHAR(10) NOT NULL,
    loyalty_card_id VARCHAR(100),
    idempotency_key VARCHAR(255) NOT NULL,
    shard_key INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    payment_id VARCHAR(255),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
