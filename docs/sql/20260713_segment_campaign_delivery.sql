alter table marketing_campaign_delivery
    add column if not exists actor_user_id bigint null,
    add column if not exists title varchar(150) null,
    add column if not exists message varchar(500) null,
    add column if not exists channel_whatsapp boolean null,
    add column if not exists delivery_status varchar(30) null,
    add column if not exists phone varchar(50) null,
    add column if not exists filter_snapshot text null,
    add column if not exists error_message varchar(500) null;

comment on column marketing_campaign_delivery.actor_user_id is 'Usuario owner/admin que confirmo la campana segmentada';
comment on column marketing_campaign_delivery.title is 'Titulo/plantilla usada al registrar la entrega';
comment on column marketing_campaign_delivery.message is 'Mensaje enviado o programado para la audiencia';
comment on column marketing_campaign_delivery.filter_snapshot is 'Resumen de filtros usados para construir la audiencia';
comment on column marketing_campaign_delivery.delivery_status is 'Estado inicial de la entrega: PENDING, SENT o SKIPPED';