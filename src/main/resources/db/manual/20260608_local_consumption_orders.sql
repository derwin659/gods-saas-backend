CREATE TABLE IF NOT EXISTS local_consumption_order (
    local_consumption_order_id bigserial PRIMARY KEY,
    tenant_id bigint NOT NULL REFERENCES tenant(tenant_id),
    branch_id bigint NOT NULL REFERENCES branch(branch_id),
    customer_id bigint REFERENCES customer(customer_id),
    customer_name varchar(160) NOT NULL,
    customer_phone varchar(40),
    status varchar(32) NOT NULL DEFAULT 'PENDING',
    total numeric(12,2) NOT NULL DEFAULT 0,
    notes text,
    admin_note text,
    sale_id bigint,
    created_at timestamp NOT NULL DEFAULT now(),
    updated_at timestamp,
    handled_at timestamp
);

CREATE TABLE IF NOT EXISTS local_consumption_order_item (
    local_consumption_order_item_id bigserial PRIMARY KEY,
    local_consumption_order_id bigint NOT NULL
        REFERENCES local_consumption_order(local_consumption_order_id)
        ON DELETE CASCADE,
    item_type varchar(20) NOT NULL,
    service_id bigint REFERENCES service(service_id),
    product_id bigint REFERENCES product(product_id),
    barber_user_id bigint REFERENCES app_user(user_id),
    item_name varchar(180) NOT NULL,
    quantity integer NOT NULL DEFAULT 1,
    unit_price numeric(12,2) NOT NULL DEFAULT 0,
    subtotal numeric(12,2) NOT NULL DEFAULT 0,
    notes text
);

CREATE INDEX IF NOT EXISTS idx_local_order_tenant_branch
    ON local_consumption_order (tenant_id, branch_id);

CREATE INDEX IF NOT EXISTS idx_local_order_status
    ON local_consumption_order (status);

CREATE INDEX IF NOT EXISTS idx_local_order_created
    ON local_consumption_order (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_local_order_item_order
    ON local_consumption_order_item (local_consumption_order_id);
