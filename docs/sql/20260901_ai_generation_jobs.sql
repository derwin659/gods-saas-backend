create table if not exists ai_generation_job (
    id varchar(50) primary key,
    session_id varchar(50) not null,
    tenant_id bigint not null,
    branch_id bigint not null,
    status varchar(24) not null,
    provider_job_id varchar(120),
    attempts integer not null default 0,
    error_message varchar(1200),
    created_at timestamp not null default current_timestamp,
    started_at timestamp,
    completed_at timestamp,
    updated_at timestamp not null default current_timestamp,
    constraint fk_ai_generation_job_session
        foreign key (session_id) references sesion_ia(id)
);

create index if not exists idx_ai_generation_job_session_created
    on ai_generation_job (session_id, created_at desc);

create index if not exists idx_ai_generation_job_status_created
    on ai_generation_job (status, created_at);