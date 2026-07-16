CREATE TABLE IF NOT EXISTS verified_business_review (
    review_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(tenant_id),
    branch_id BIGINT NOT NULL REFERENCES branch(branch_id),
    customer_id BIGINT NOT NULL REFERENCES customer(customer_id),
    appointment_id BIGINT NOT NULL REFERENCES appointment(appointment_id),
    rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comment VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_verified_review_appointment UNIQUE (appointment_id)
);
CREATE INDEX IF NOT EXISTS idx_verified_review_branch_created ON verified_business_review(branch_id, created_at DESC);