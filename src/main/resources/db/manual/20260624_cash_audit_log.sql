-- Auditoria de caja: ventas y movimientos editados/eliminados.
-- Ejecutar una sola vez por base de datos.

create table if not exists cash_audit_log (
    cash_audit_log_id bigserial primary key,
    tenant_id bigint not null references tenant(tenant_id),
    branch_id bigint not null references branch(branch_id),
    cash_register_id bigint null references cash_register(cash_register_id),
    actor_user_id bigint null references app_user(user_id),
    entity_type varchar(40) not null,
    entity_id bigint not null,
    action varchar(40) not null,
    reason varchar(500) not null,
    before_snapshot text null,
    after_snapshot text null,
    created_at timestamp not null default now()
);

create index if not exists idx_cash_audit_tenant_branch_created
    on cash_audit_log (tenant_id, branch_id, created_at desc);

create index if not exists idx_cash_audit_entity
    on cash_audit_log (entity_type, entity_id);

create index if not exists idx_cash_audit_cash_register
    on cash_audit_log (cash_register_id);