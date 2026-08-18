CREATE TABLE IF NOT EXISTS showcase_favorite (
    favorite_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(tenant_id) ON DELETE CASCADE,
    showcase_id BIGINT NOT NULL REFERENCES professional_showcase(showcase_id) ON DELETE CASCADE,
    client_user_id BIGINT NOT NULL REFERENCES app_user(user_id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_showcase_favorite_client UNIQUE (showcase_id, client_user_id)
);

CREATE INDEX IF NOT EXISTS idx_showcase_favorite_client
    ON showcase_favorite (tenant_id, client_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_showcase_favorite_showcase
    ON showcase_favorite (showcase_id);