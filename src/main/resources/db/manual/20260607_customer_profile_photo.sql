ALTER TABLE customer
    ADD COLUMN IF NOT EXISTS photo_url text;

ALTER TABLE customer
    ADD COLUMN IF NOT EXISTS photo_public_id text;
