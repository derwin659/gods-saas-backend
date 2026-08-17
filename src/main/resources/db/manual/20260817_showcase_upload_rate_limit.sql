CREATE TABLE IF NOT EXISTS showcase_upload_rate_limit (
    tenant_id BIGINT NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    subject_id BIGINT NOT NULL,
    media_type VARCHAR(16) NOT NULL,
    window_started_at TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    active_token UUID NULL,
    active_since TIMESTAMPTZ NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, scope_type, subject_id, media_type),
    CONSTRAINT chk_showcase_upload_scope CHECK (scope_type IN ('USER', 'TENANT')),
    CONSTRAINT chk_showcase_upload_media CHECK (media_type IN ('IMAGE', 'VIDEO')),
    CONSTRAINT chk_showcase_upload_attempts CHECK (attempts >= 0)
);

CREATE INDEX IF NOT EXISTS idx_showcase_upload_rate_limit_updated
    ON showcase_upload_rate_limit (updated_at);

COMMENT ON TABLE showcase_upload_rate_limit IS
    'Rate limit distribuido de cargas de vitrina por tenant, usuario y tipo de medio.';