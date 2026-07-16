ALTER TABLE verified_business_review
    ALTER COLUMN appointment_id DROP NOT NULL;

ALTER TABLE verified_business_review
    ADD COLUMN IF NOT EXISTS sale_id BIGINT REFERENCES sale(sale_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_verified_review_sale
    ON verified_business_review(sale_id)
    WHERE sale_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'ck_verified_review_visit_source'
    ) THEN
        ALTER TABLE verified_business_review
            ADD CONSTRAINT ck_verified_review_visit_source
            CHECK (appointment_id IS NOT NULL OR sale_id IS NOT NULL);
    END IF;
END $$;