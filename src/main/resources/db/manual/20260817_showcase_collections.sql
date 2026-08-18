ALTER TABLE professional_showcase
    ADD COLUMN IF NOT EXISTS collection_name VARCHAR(80);

CREATE INDEX IF NOT EXISTS idx_professional_showcase_collection
    ON professional_showcase (tenant_id, collection_name)
    WHERE collection_name IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_professional_showcase_publication_time
    ON professional_showcase (tenant_id, status, published_at DESC);