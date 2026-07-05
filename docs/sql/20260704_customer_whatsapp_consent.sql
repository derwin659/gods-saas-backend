alter table customer
    add column if not exists whatsapp_transactional_enabled boolean not null default true,
    add column if not exists whatsapp_marketing_enabled boolean not null default false,
    add column if not exists whatsapp_opted_out_at timestamp null;

comment on column customer.whatsapp_transactional_enabled is 'Autoriza recibos, citas, recordatorios y mensajes operativos por WhatsApp';
comment on column customer.whatsapp_marketing_enabled is 'Autoriza campañas y promociones por WhatsApp';
comment on column customer.whatsapp_opted_out_at is 'Fecha de baja total de WhatsApp; bloquea cualquier envío';