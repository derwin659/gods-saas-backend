-- Prioridad 13: negocios seguidos/favoritos por cliente.
CREATE TABLE IF NOT EXISTS client_business_follow (
    client_business_follow_id BIGSERIAL PRIMARY KEY,
    follower_phone VARCHAR(50) NOT NULL,
    tenant_id BIGINT NOT NULL REFERENCES tenant(tenant_id),
    source_customer_id BIGINT NULL REFERENCES customer(customer_id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_client_business_follow_phone_tenant UNIQUE (follower_phone, tenant_id)
);
CREATE INDEX IF NOT EXISTS idx_client_business_follow_tenant ON client_business_follow (tenant_id);
CREATE INDEX IF NOT EXISTS idx_client_business_follow_phone ON client_business_follow (follower_phone);
