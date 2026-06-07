ALTER TABLE product
    ADD COLUMN IF NOT EXISTS public_visible boolean NOT NULL DEFAULT false;

ALTER TABLE product
    ADD COLUMN IF NOT EXISTS public_featured boolean NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS product_order (
    product_order_id bigserial PRIMARY KEY,
    tenant_id bigint NOT NULL REFERENCES tenant(tenant_id),
    branch_id bigint NOT NULL REFERENCES branch(branch_id),
    product_id bigint NOT NULL REFERENCES product(product_id),
    customer_name varchar(160) NOT NULL,
    customer_phone varchar(40) NOT NULL,
    quantity integer NOT NULL DEFAULT 1,
    unit_price numeric(12,2) NOT NULL DEFAULT 0,
    total numeric(12,2) NOT NULL DEFAULT 0,
    payment_method varchar(40) NOT NULL DEFAULT 'PAY_AT_SHOP',
    payment_operation_number varchar(80),
    payment_capture_url text,
    status varchar(30) NOT NULL DEFAULT 'PENDING',
    notes text,
    admin_note text,
    sale_id bigint,
    expires_at timestamp,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp NOT NULL DEFAULT now(),
    validated_at timestamp,
    delivered_at timestamp
);

CREATE INDEX IF NOT EXISTS idx_product_order_tenant_branch_status
    ON product_order (tenant_id, branch_id, status);

CREATE INDEX IF NOT EXISTS idx_product_order_created_at
    ON product_order (created_at DESC);
