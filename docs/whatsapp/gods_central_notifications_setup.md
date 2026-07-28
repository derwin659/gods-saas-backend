# GODS Notificaciones: puesta en produccion

## Estrategia inicial

Super Gods opera un unico remitente central de WhatsApp llamado
`GODS Notificaciones`. Los negocios no ingresan credenciales ni conectan su
numero en esta etapa. Cada owner verifica el WhatsApp receptor iniciando una
conversacion con el numero oficial y enviando el mensaje seguro generado por
GODS. No depende de una plantilla Authentication.

La conexion de un remitente propio por negocio queda reservada para una fase
posterior Growth/VIP mediante Embedded Signup y aislamiento por tenant.

## Orden de activacion

1. Ejecutar `docs/sql/20260726_owner_whatsapp_recipient_verification.sql`.
2. Crear y aprobar en Twilio la plantilla Utility `owner_new_booking_v1` con
   variables `{{1}}` a `{{10}}`.
3. Configurar Railway:

```text
WHATSAPP_CENTRAL_ENABLED=true
WHATSAPP_CENTRAL_PROVIDER=TWILIO
WHATSAPP_CENTRAL_SENDER_LABEL=GODS Notificaciones
WHATSAPP_TWILIO_ENABLED=true
TWILIO_ACCOUNT_SID=ACxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_AUTH_TOKEN=xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_WHATSAPP_FROM_NUMBER=+<numero_aprobado>
TWILIO_OWNER_BOOKING_CONTENT_SID=HXxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
TWILIO_INBOUND_WEBHOOK_URL=https://gods-saas-backend-production.up.railway.app/api/public/whatsapp/twilio/inbound
TWILIO_VALIDATE_INBOUND_SIGNATURE=true
```

`TWILIO_STATUS_CALLBACK_URL` es opcional. Para verificar receptores se requiere
`TWILIO_WHATSAPP_FROM_NUMBER`; un Messaging Service SID por si solo no permite
construir el enlace hacia el numero oficial.

4. En Twilio abrir el sender oficial y configurar `A message comes in` con:

```text
Metodo: POST
URL: https://gods-saas-backend-production.up.railway.app/api/public/whatsapp/twilio/inbound
```

La URL de Twilio y `TWILIO_INBOUND_WEBHOOK_URL` deben ser identicas porque
forman parte de la firma. No agregar slash final en un solo lado.

5. Desplegar backend despues del SQL.
6. Desplegar web y publicar el nuevo build movil.
7. En `WhatsApp y mensajes`, registrar el numero, abrir WhatsApp, enviar el
   mensaje sin modificarlo, volver a GODS y comprobar.
8. Activar el aviso de reservas.
9. Crear una reserva cliente de prueba y validar `notification_delivery`.

## Controles incluidos

- Codigo aleatorio de seis digitos dentro de un mensaje iniciado por el owner.
- Hash BCrypt, sin codigo en texto plano ni logs.
- Vigencia de 10 minutos.
- Espera de 60 segundos para generar otro enlace.
- Maximo de 5 intentos.
- El numero que envia el mensaje debe coincidir con el numero pendiente.
- Firma `X-Twilio-Signature` obligatoria en produccion.
- El numero del perfil cambia solo despues de verificar.
- Cambiar el telefono despues invalida la verificacion anterior.
- No se crea entrega WhatsApp para un receptor no verificado.
- IN_APP y PUSH permanecen como respaldo.
