BEGIN;

ALTER TABLE verified_business_review
    ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(24) NOT NULL DEFAULT 'PUBLISHED',
    ADD COLUMN IF NOT EXISTS report_reason VARCHAR(40),
    ADD COLUMN IF NOT EXISTS report_details VARCHAR(500),
    ADD COLUMN IF NOT EXISTS reported_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS reported_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS moderated_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS moderated_by_user_id BIGINT,
    ADD COLUMN IF NOT EXISTS moderation_note VARCHAR(500);

UPDATE verified_business_review
SET moderation_status = 'PUBLISHED'
WHERE moderation_status IS NULL;

CREATE INDEX IF NOT EXISTS idx_verified_review_moderation_status
    ON verified_business_review (moderation_status, reported_at DESC);

COMMIT;