BEGIN;

ALTER TABLE branch
    ADD COLUMN IF NOT EXISTS walk_in_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS walk_in_paused BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS walk_in_estimated_wait_minutes INTEGER,
    ADD COLUMN IF NOT EXISTS walk_in_message VARCHAR(200);

ALTER TABLE branch
    DROP CONSTRAINT IF EXISTS ck_branch_walk_in_wait;

ALTER TABLE branch
    ADD CONSTRAINT ck_branch_walk_in_wait
    CHECK (walk_in_estimated_wait_minutes IS NULL OR
           walk_in_estimated_wait_minutes BETWEEN 0 AND 240);

COMMIT;