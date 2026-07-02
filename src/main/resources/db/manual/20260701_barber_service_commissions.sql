CREATE TABLE IF NOT EXISTS barber_service_commission (
    barber_service_commission_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(tenant_id),
    branch_id BIGINT NOT NULL REFERENCES branch(branch_id),
    barber_user_id BIGINT NOT NULL REFERENCES app_user(user_id),
    service_id BIGINT NOT NULL REFERENCES service(service_id),
    commission_percentage NUMERIC(5,2) NOT NULL CHECK (commission_percentage >= 0 AND commission_percentage <= 100),
    CONSTRAINT uk_barber_service_commission UNIQUE (tenant_id, branch_id, barber_user_id, service_id)
);
CREATE INDEX IF NOT EXISTS idx_barber_service_commission_lookup
    ON barber_service_commission (tenant_id, branch_id, barber_user_id);
