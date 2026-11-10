-- Apply before enabling BOOKING_WAITLIST_ENABLED=true. No existing data changes.
CREATE TABLE IF NOT EXISTS booking_waitlist (
 id bigserial PRIMARY KEY,
 tenant_id bigint NOT NULL REFERENCES tenant(tenant_id),
 customer_id bigint NOT NULL REFERENCES customer(customer_id),
 branch_id bigint NOT NULL REFERENCES branch(branch_id),
 service_id bigint NOT NULL REFERENCES service(service_id),
 barber_id bigint,
 requested_date date NOT NULL, earliest_time time NOT NULL, latest_time time NOT NULL,
 status varchar(20) NOT NULL CHECK(status IN ('WAITING','AVAILABLE','CANCELLED','EXPIRED')),
 offered_time time, offer_expires_at timestamptz, checked_at timestamptz NOT NULL,
 created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, version bigint NOT NULL DEFAULT 0,
 CHECK(earliest_time < latest_time)
);
CREATE INDEX IF NOT EXISTS booking_waitlist_customer ON booking_waitlist(tenant_id,customer_id,created_at DESC);
CREATE INDEX IF NOT EXISTS booking_waitlist_pending ON booking_waitlist(checked_at,created_at) WHERE status IN ('WAITING','AVAILABLE');
CREATE INDEX IF NOT EXISTS booking_waitlist_branch ON booking_waitlist(tenant_id,branch_id,status);
