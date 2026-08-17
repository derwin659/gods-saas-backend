CREATE TABLE IF NOT EXISTS showcase_event (
    showcase_event_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(tenant_id) ON DELETE CASCADE,
    showcase_id BIGINT NOT NULL REFERENCES professional_showcase(showcase_id) ON DELETE CASCADE,
    event_type VARCHAR(30) NOT NULL,
    viewer_key VARCHAR(64) NOT NULL,
    event_date DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_showcase_event_type CHECK (
        event_type IN ('VIEW', 'VIDEO_START', 'VIDEO_COMPLETE', 'RESERVE_CLICK', 'BOOKING_CONFIRMED')
    ),
    CONSTRAINT uq_showcase_event_daily_viewer UNIQUE
        (showcase_id, event_type, viewer_key, event_date)
);

CREATE INDEX IF NOT EXISTS idx_showcase_event_tenant_created
    ON showcase_event (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_showcase_event_showcase_type
    ON showcase_event (showcase_id, event_type);