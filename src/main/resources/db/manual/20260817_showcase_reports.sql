CREATE TABLE IF NOT EXISTS showcase_report (
    report_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(tenant_id) ON DELETE CASCADE,
    showcase_id BIGINT NOT NULL REFERENCES professional_showcase(showcase_id) ON DELETE CASCADE,
    reason VARCHAR(40) NOT NULL,
    details VARCHAR(300),
    reporter_key VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    reviewed_by_user_id BIGINT REFERENCES app_user(user_id),
    reviewed_at TIMESTAMPTZ,
    resolution_note VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_showcase_report_reason CHECK (
        reason IN ('INAPPROPRIATE', 'PERSONAL_DATA', 'NO_CONSENT', 'MISLEADING', 'SPAM', 'OTHER')
    ),
    CONSTRAINT chk_showcase_report_status CHECK (
        status IN ('OPEN', 'DISMISSED', 'REVIEWED', 'CONTENT_HIDDEN')
    ),
    CONSTRAINT uq_showcase_report_reporter UNIQUE (showcase_id, reporter_key)
);

CREATE INDEX IF NOT EXISTS idx_showcase_report_tenant_status
    ON showcase_report (tenant_id, status, created_at DESC);