# Plantilla WhatsApp: owner_new_booking_v1

Esta plantilla operativa permite avisar al owner, administradores o profesional
cuando se crea una reserva. El backend no suplanta el numero del cliente: envia
desde el numero oficial conectado y muestra el telefono del cliente dentro del
mensaje.

## Meta Cloud API

- Nombre: `owner_new_booking_v1`
- Categoria recomendada: `UTILITY`
- Idioma: Espanol (`es`)
- Cuerpo exacto:

```text
Nueva reserva #{{1}}
Cliente: {{2}}
WhatsApp: {{3}}
Negocio / sede: {{4}}
Servicio: {{5}}
Profesional: {{6}}
Fecha: {{7}}
Horario: {{8}}
Estado de pago: {{9}}
Abrir agenda: {{10}}
```

Variables enviadas por backend:

1. ID de la reserva.
2. Nombre del cliente.
3. Telefono del cliente.
4. Negocio y sede.
5. Servicio.
6. Profesional.
7. Fecha local.
8. Horario local.
9. Estado, adelanto y total.
10. URL de agenda owner.

El nombre e idioma pueden sobrescribirse en `tenant_settings.schedule_config`
con `whatsappOwnerBookingTemplateName` y
`whatsappOwnerBookingTemplateLanguage`. No se requiere SQL para usar los
valores predeterminados.

## Twilio WhatsApp

Crear un Content Template equivalente con las mismas variables `{{1}}` a
`{{10}}` y configurar en Railway:

```text
TWILIO_OWNER_BOOKING_CONTENT_SID=HXxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

El backend envia `ContentSid` y `ContentVariables` para reservas internas. Los
otros mensajes existentes continuan usando texto libre cuando el proveedor lo
permite.

## Activacion En Super Gods

1. Conectar Meta Cloud API o Twilio y dejar el estado en `CONNECTED`.
2. Verificar que el owner tenga telefono con codigo de pais en `app_user.phone`.
3. Entrar a Owner > Configuracion > WhatsApp y mensajes.
4. Activar `Avisarme nuevas reservas por WhatsApp`.
5. Opcionalmente incluir administradores, profesional o citas creadas por el
   equipo.
6. Crear una reserva de prueba desde cliente y revisar
   `notification_delivery` si no llega.

La reserva nunca se revierte si el proveedor falla. IN_APP y PUSH permanecen
como respaldo y el error queda registrado en la entrega.