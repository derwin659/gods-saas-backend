alter table sale_item
    add column if not exists commission_percentage_applied numeric(5,2),
    add column if not exists commission_amount_applied numeric(12,2);

comment on column sale_item.commission_percentage_applied is 'Porcentaje de comisión congelado al registrar o editar el servicio';
comment on column sale_item.commission_amount_applied is 'Monto de comisión histórico correspondiente al servicio vendido';

create index if not exists idx_sale_item_commission_snapshot
    on sale_item (barber_user_id, commission_amount_applied)
    where service_id is not null;
