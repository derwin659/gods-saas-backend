-- Apply before deploying the package-enabled backend. Existing services remain unchanged.
ALTER TABLE service ADD COLUMN IF NOT EXISTS package_service_ids jsonb;
