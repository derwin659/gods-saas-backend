BEGIN;

CREATE TABLE IF NOT EXISTS professional_showcase (
    showcase_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(tenant_id),
    branch_id BIGINT NOT NULL REFERENCES branch(branch_id),
    professional_user_id BIGINT NOT NULL REFERENCES app_user(user_id),
    service_id BIGINT NULL REFERENCES service(service_id),
    title VARCHAR(120) NOT NULL,
    description VARCHAR(600),
    media_type VARCHAR(20) NOT NULL DEFAULT 'IMAGE',
    image_url VARCHAR(700) NOT NULL,
    thumbnail_url VARCHAR(700),
    image_public_id VARCHAR(500),
    duration_seconds INTEGER,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL',
    client_image_consent BOOLEAN NOT NULL DEFAULT FALSE,
    rejection_reason VARCHAR(300),
    moderated_by_user_id BIGINT NULL REFERENCES app_user(user_id),
    moderated_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP NULL,
    archived_at TIMESTAMP NULL,
    CONSTRAINT ck_professional_showcase_status CHECK (status IN ('PENDING_APPROVAL','PUBLISHED','REJECTED','ARCHIVED')),
    CONSTRAINT ck_professional_showcase_media CHECK (media_type IN ('IMAGE','VIDEO')),
    CONSTRAINT ck_professional_showcase_duration CHECK (duration_seconds IS NULL OR duration_seconds BETWEEN 0 AND 90)
);

ALTER TABLE professional_showcase ADD COLUMN IF NOT EXISTS media_type VARCHAR(20) NOT NULL DEFAULT 'IMAGE';
ALTER TABLE professional_showcase ADD COLUMN IF NOT EXISTS thumbnail_url VARCHAR(700);
ALTER TABLE professional_showcase ADD COLUMN IF NOT EXISTS duration_seconds INTEGER;

DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_professional_showcase_media') THEN
        ALTER TABLE professional_showcase ADD CONSTRAINT ck_professional_showcase_media CHECK (media_type IN ('IMAGE','VIDEO'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_professional_showcase_duration') THEN
        ALTER TABLE professional_showcase ADD CONSTRAINT ck_professional_showcase_duration CHECK (duration_seconds IS NULL OR duration_seconds BETWEEN 0 AND 90);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_showcase_tenant_status ON professional_showcase (tenant_id,status,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_showcase_branch_published ON professional_showcase (branch_id,status,published_at DESC);
CREATE INDEX IF NOT EXISTS idx_showcase_professional ON professional_showcase (professional_user_id,created_at DESC);
CREATE INDEX IF NOT EXISTS idx_showcase_public_media ON professional_showcase (tenant_id,branch_id,media_type,status,published_at DESC);

COMMIT;