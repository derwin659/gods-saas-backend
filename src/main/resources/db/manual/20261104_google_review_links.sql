-- Apply before publishing the backend. Repeatable; no existing links or messages are enabled.
ALTER TABLE branch ADD COLUMN IF NOT EXISTS google_review_url varchar(1000);
ALTER TABLE branch ADD COLUMN IF NOT EXISTS google_review_whatsapp_enabled boolean NOT NULL DEFAULT false;
