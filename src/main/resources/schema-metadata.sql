CREATE TABLE IF NOT EXISTS bulk_payment_requests (
    id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_count INT NOT NULL,
    completed_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
