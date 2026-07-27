# Plantilla WhatsApp: owner_new_booking_v1

Esta plantilla operativa permite avisar al owner, administradores o profesional
cuando se crea una reserva. El backend no suplanta el numero del cliente: envia
desde el numero central oficial GODS Notificaciones y muestra el telefono del
cliente dentro del mensaje.

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

En la primera etapa el nombre e idioma pertenecen a la configuracion global de
`GODS Notificaciones`; ningun tenant debe ingresar credenciales ni Content SID.

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

1. Ejecutar `docs/sql/20260726_owner_whatsapp_recipient_verification.sql`.
2. Configurar una sola cuenta/remitente Twilio de `GODS Notificaciones` en
   Railway con ambos Content SID aprobados.
3. Entrar a Owner > Configuracion > WhatsApp y mensajes.
4. Solicitar el OTP y verificar el numero receptor.
5. Activar `Avisarme nuevas reservas por WhatsApp`.
6. Opcionalmente incluir administradores o profesionales que tambien hayan
   verificado su propio numero.
7. Crear una reserva de prueba desde cliente y revisar
   `notification_delivery` si no llega.

La reserva nunca se revierte si el proveedor falla. IN_APP y PUSH permanecen
como respaldo y el error queda registrado en la entrega.