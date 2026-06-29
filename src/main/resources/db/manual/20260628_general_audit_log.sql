-- Auditoria general de acciones sensibles del sistema.
-- Ejecutar una sola vez por base de datos antes de desplegar la funcionalidad.

create table if not exists general_audit_log (
    general_audit_log_id bigserial primary key,
    tenant_id bigint not null,
    branch_id bigint null,
    actor_user_id bigint null,
    actor_user_name varchar(180) null,
    actor_role varchar(40) null,
    entity_type varchar(60) not null,
    entity_id bigint null,
    action varchar(60) not null,
    reason varchar(500) null,
    before_snapshot text null,
    after_snapshot text null,
    created_at timestamp not null default now()
);

create index if not exists idx_general_audit_tenant_created
    on general_audit_log (tenant_id, created_at desc);

create index if not exists idx_general_audit_actor
    on general_audit_log (tenant_id, actor_user_id);

create index if not exists idx_general_audit_branch_created
    on general_audit_log (tenant_id, branch_id, created_at desc);

create index if not exists idx_general_audit_entity
    on general_audit_log (tenant_id, entity_type, entity_id);
