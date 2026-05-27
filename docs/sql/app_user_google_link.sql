ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS google_subject VARCHAR(120),
    ADD COLUMN IF NOT EXISTS google_email VARCHAR(150),
    ADD COLUMN IF NOT EXISTS google_name VARCHAR(180),
    ADD COLUMN IF NOT EXISTS google_picture_url VARCHAR(600),
    ADD COLUMN IF NOT EXISTS google_linked_at TIMESTAMP;

CREATE UNIQUE INDEX IF NOT EXISTS ux_app_user_google_subject
    ON app_user (google_subject)
    WHERE google_subject IS NOT NULL;
