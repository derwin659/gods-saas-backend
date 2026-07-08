alter table customer
    add column if not exists customer_notes text null,
    add column if not exists preferred_services text null,
    add column if not exists customer_restrictions text null,
    add column if not exists preferred_contact_channel varchar(30) null,
    add column if not exists favorite_barber_name varchar(150) null;

comment on column customer.customer_notes is 'Notas internas del owner sobre preferencias y trato del cliente';
comment on column customer.preferred_services is 'Servicios preferidos declarados o ajustados por el owner';
comment on column customer.customer_restrictions is 'Restricciones, alergias o cuidados que deben considerarse al atender';
comment on column customer.preferred_contact_channel is 'Canal favorito de contacto: WHATSAPP, PHONE, EMAIL u otro valor operativo';
comment on column customer.favorite_barber_name is 'Barbero favorito editable por el owner, como override del calculado por historial';