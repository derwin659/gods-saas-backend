-- Apply before enabling cut references. Legacy service/sale queries do not touch this table.
CREATE TABLE IF NOT EXISTS cut_reference (
 id BIGSERIAL PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 sale_id BIGINT NOT NULL UNIQUE REFERENCES sale(sale_id) ON DELETE CASCADE,
 public_id VARCHAR(255) NOT NULL,
 media_type VARCHAR(20) NOT NULL CHECK (media_type IN ('IMAGE','VIDEO')),
 format VARCHAR(30) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_cut_reference_tenant_sale ON cut_reference(tenant_id,sale_id);
