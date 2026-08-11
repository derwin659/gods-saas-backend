BEGIN;
ALTER TABLE professional_showcase ADD COLUMN IF NOT EXISTS origin_type VARCHAR(30) NOT NULL DEFAULT 'PROFESSIONAL_WORK';
ALTER TABLE professional_showcase ADD COLUMN IF NOT EXISTS visibility_scope VARCHAR(30) NOT NULL DEFAULT 'ORIGIN_BRANCH';
ALTER TABLE professional_showcase ADD COLUMN IF NOT EXISTS category VARCHAR(80);
ALTER TABLE professional_showcase ADD COLUMN IF NOT EXISTS featured BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE professional_showcase ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0;
ALTER TABLE professional_showcase ADD COLUMN IF NOT EXISTS created_by_user_id BIGINT REFERENCES app_user(user_id);
ALTER TABLE professional_showcase ALTER COLUMN branch_id DROP NOT NULL;
ALTER TABLE professional_showcase ALTER COLUMN professional_user_id DROP NOT NULL;
CREATE TABLE IF NOT EXISTS professional_showcase_branch (
 showcase_id BIGINT NOT NULL REFERENCES professional_showcase(showcase_id) ON DELETE CASCADE,
 branch_id BIGINT NOT NULL REFERENCES branch(branch_id) ON DELETE CASCADE,
 PRIMARY KEY(showcase_id,branch_id)
);
CREATE INDEX IF NOT EXISTS idx_showcase_origin_scope ON professional_showcase(tenant_id,origin_type,visibility_scope,status,published_at DESC);
CREATE INDEX IF NOT EXISTS idx_showcase_selected_branch ON professional_showcase_branch(branch_id,showcase_id);
COMMIT;