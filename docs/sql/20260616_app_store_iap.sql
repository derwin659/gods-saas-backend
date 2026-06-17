ALTER TABLE subscription
    ADD COLUMN IF NOT EXISTS app_store_product_id varchar(120),
    ADD COLUMN IF NOT EXISTS app_store_transaction_id varchar(120),
    ADD COLUMN IF NOT EXISTS app_store_original_transaction_id varchar(120),
    ADD COLUMN IF NOT EXISTS app_store_environment varchar(40),
    ADD COLUMN IF NOT EXISTS app_store_expires_at timestamp;

CREATE TABLE IF NOT EXISTS app_store_purchase (
    id bigserial PRIMARY KEY,
    tenant_id bigint NOT NULL,
    subscription_id bigint,
    plan varchar(80) NOT NULL,
    product_id varchar(120) NOT NULL,
    transaction_id varchar(120),
    original_transaction_id varchar(120),
    app_account_token varchar(120),
    environment varchar(40),
    status varchar(40) NOT NULL,
    purchased_at timestamp,
    expires_at timestamp,
    receipt_data text,
    apple_response text,
    error_message text,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_app_store_purchase_tenant
    ON app_store_purchase (tenant_id);

CREATE INDEX IF NOT EXISTS idx_app_store_purchase_original_tx
    ON app_store_purchase (original_transaction_id);

CREATE INDEX IF NOT EXISTS idx_subscription_app_store_original_tx
    ON subscription (app_store_original_transaction_id);
