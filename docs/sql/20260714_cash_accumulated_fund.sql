CREATE TABLE IF NOT EXISTS cash_fund_movement (
    cash_fund_movement_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(tenant_id),
    branch_id BIGINT NOT NULL REFERENCES branch(branch_id),
    cash_register_id BIGINT NULL REFERENCES cash_register(cash_register_id),
    actor_user_id BIGINT NULL REFERENCES app_user(user_id),
    type VARCHAR(40) NOT NULL,
    payment_method VARCHAR(30) NOT NULL DEFAULT 'CASH',
    amount NUMERIC(12, 2) NOT NULL,
    concept VARCHAR(200) NOT NULL,
    note VARCHAR(500),
    movement_date TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_cash_fund_movement_tenant_branch_date
    ON cash_fund_movement (tenant_id, branch_id, movement_date DESC);

CREATE INDEX IF NOT EXISTS idx_cash_fund_movement_cash_register
    ON cash_fund_movement (cash_register_id);