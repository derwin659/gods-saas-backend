alter table service
    add column if not exists deleted_at timestamp,
    add column if not exists deleted_by_user_id bigint,
    add column if not exists deletion_reason varchar(500);

create index if not exists idx_service_tenant_deleted
    on service (tenant_id, deleted_at);
