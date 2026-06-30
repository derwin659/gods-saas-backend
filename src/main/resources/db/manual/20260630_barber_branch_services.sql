create table if not exists barber_branch_service (
    barber_branch_service_id bigserial primary key,
    tenant_id bigint not null references tenant(tenant_id),
    branch_id bigint not null references branch(branch_id),
    barber_user_id bigint not null references app_user(user_id),
    service_id bigint not null references service(service_id),
    constraint uk_barber_branch_service unique (tenant_id, branch_id, barber_user_id, service_id)
);
create index if not exists idx_barber_branch_service_lookup on barber_branch_service(tenant_id, branch_id, barber_user_id);
