create table if not exists customer_follow_up (
    customer_follow_up_id bigserial primary key,
    tenant_id bigint not null references tenant(tenant_id),
    customer_id bigint not null references customer(customer_id),
    actor_user_id bigint null references app_user(user_id),
    title varchar(160) not null,
    message varchar(800) not null,
    channel varchar(30) not null default 'WHATSAPP',
    status varchar(30) not null default 'PENDING',
    scheduled_at timestamp null,
    completed_at timestamp null,
    created_at timestamp not null default now(),
    updated_at timestamp not null default now()
);

create index if not exists idx_customer_follow_up_tenant_customer
    on customer_follow_up(tenant_id, customer_id, created_at desc);

create index if not exists idx_customer_follow_up_status
    on customer_follow_up(tenant_id, status, scheduled_at);
