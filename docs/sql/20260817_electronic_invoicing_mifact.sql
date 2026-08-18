-- Base segura e idempotente. Esta migracion NO emite documentos ni activa Mifact.
CREATE TABLE IF NOT EXISTS electronic_invoicing_settings (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL UNIQUE REFERENCES tenant(tenant_id),
    fiscal_ruc VARCHAR(11) NOT NULL, legal_name VARCHAR(250) NOT NULL,
    commercial_name VARCHAR(250), fiscal_address VARCHAR(500) NOT NULL,
    ubigeo VARCHAR(6) NOT NULL, sales_point_code VARCHAR(20) NOT NULL,
    annex_code VARCHAR(4) NOT NULL DEFAULT '0000',
    invoice_series VARCHAR(4) NOT NULL DEFAULT 'F001',
    receipt_series VARCHAR(4) NOT NULL DEFAULT 'B001',
    next_invoice_number BIGINT NOT NULL DEFAULT 1,
    next_receipt_number BIGINT NOT NULL DEFAULT 1,
    credential_alias VARCHAR(50) NOT NULL,
    igv_rate NUMERIC(5,2) NOT NULL DEFAULT 18.00,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ei_ruc CHECK (fiscal_ruc ~ '^[0-9]{11}$'),
    CONSTRAINT ck_ei_ubigeo CHECK (ubigeo ~ '^[0-9]{6}$'),
    CONSTRAINT ck_ei_invoice_series CHECK (invoice_series ~ '^F[A-Z0-9]{3}$'),
    CONSTRAINT ck_ei_receipt_series CHECK (receipt_series ~ '^B[A-Z0-9]{3}$'),
    CONSTRAINT ck_ei_numbers CHECK (next_invoice_number > 0 AND next_receipt_number > 0)
);

CREATE TABLE IF NOT EXISTS electronic_document (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(tenant_id),
    branch_id BIGINT NOT NULL REFERENCES branch(branch_id),
    sale_id BIGINT NOT NULL REFERENCES sale(sale_id),
    document_type VARCHAR(30) NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    provider VARCHAR(30) NOT NULL DEFAULT 'MIFACT',
    series VARCHAR(10), sequence VARCHAR(20), provider_status VARCHAR(10),
    sunat_response_code VARCHAR(30), sunat_description VARCHAR(2000),
    error_message VARCHAR(3000), document_url VARCHAR(1000),
    request_hash VARCHAR(64) NOT NULL,
    request_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    response_snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP, accepted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_electronic_document_type CHECK
        (document_type IN ('INVOICE','RECEIPT','CREDIT_NOTE','DEBIT_NOTE')),
    CONSTRAINT ck_electronic_document_status CHECK
        (status IN ('DRAFT','PENDING','PROCESSING','ACCEPTED',
         'ACCEPTED_WITH_OBSERVATIONS','REJECTED','VOID_PENDING','VOIDED','ERROR')),
    CONSTRAINT uq_electronic_document_sale_type
        UNIQUE (tenant_id, sale_id, document_type),
    CONSTRAINT uq_electronic_document_number
        UNIQUE (tenant_id, document_type, series, sequence)
);
CREATE INDEX IF NOT EXISTS idx_electronic_document_tenant_status
    ON electronic_document (tenant_id, status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_electronic_document_branch_created
    ON electronic_document (branch_id, created_at DESC);
COMMENT ON COLUMN electronic_document.request_snapshot IS
    'Snapshot fiscal sin TOKEN ni otros secretos; no guardar credenciales del proveedor.';
