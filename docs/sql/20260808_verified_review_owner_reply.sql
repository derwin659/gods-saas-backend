BEGIN;

ALTER TABLE verified_business_review
    ADD COLUMN IF NOT EXISTS owner_reply VARCHAR(500),
    ADD COLUMN IF NOT EXISTS owner_replied_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS owner_replied_by_user_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_verified_review_owner_reply_actor
    ON verified_business_review (owner_replied_by_user_id)
    WHERE owner_replied_by_user_id IS NOT NULL;

COMMIT;