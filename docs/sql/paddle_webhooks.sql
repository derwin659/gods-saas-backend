ALTER TABLE subscription
    ADD COLUMN IF NOT EXISTS paddle_customer_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS paddle_subscription_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS paddle_status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS paddle_last_transaction_id VARCHAR(80);

ALTER TABLE subscription_payments
    ADD COLUMN IF NOT EXISTS provider VARCHAR(40),
    ADD COLUMN IF NOT EXISTS provider_payment_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS provider_subscription_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS provider_customer_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS provider_currency VARCHAR(3);

CREATE UNIQUE INDEX IF NOT EXISTS uq_subscription_payments_provider_payment
    ON subscription_payments (provider, provider_payment_id)
    WHERE provider IS NOT NULL AND provider_payment_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS paddle_webhook_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(120) NOT NULL UNIQUE,
    event_type VARCHAR(80) NOT NULL,
    paddle_object_id VARCHAR(120),
    tenant_id BIGINT,
    status VARCHAR(30) NOT NULL,
    error_message TEXT,
    payload TEXT,
    received_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_paddle_webhook_events_tenant
    ON paddle_webhook_events (tenant_id);

CREATE INDEX IF NOT EXISTS idx_paddle_webhook_events_type
    ON paddle_webhook_events (event_type);
