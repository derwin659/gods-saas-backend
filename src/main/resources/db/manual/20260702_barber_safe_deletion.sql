alter table app_user
    add column if not exists retired_at timestamp,
    add column if not exists retired_by_user_id bigint,
    add column if not exists retirement_reason varchar(500);

create index if not exists idx_app_user_tenant_retired
    on app_user (tenant_id, retired_at);
