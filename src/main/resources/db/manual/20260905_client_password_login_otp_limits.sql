-- Cliente: login diario con telefono + contraseña.
-- OTP queda para activacion inicial, recuperacion y cambio de telefono.

alter table customer
    add column if not exists password_hash varchar(255);

create index if not exists idx_customer_tenant_phone_active
    on customer (tenant_id, telefono, activo);

create index if not exists idx_otp_code_tenant_phone_created_at
    on otp_code (tenant_id, phone, created_at desc);

create index if not exists idx_otp_code_tenant_phone_unused_created_at
    on otp_code (tenant_id, phone, used, created_at desc);