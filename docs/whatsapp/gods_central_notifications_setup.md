# GODS Notificaciones: puesta en produccion

## Estrategia inicial

Super Gods opera un unico remitente central de WhatsApp llamado
`GODS Notificaciones`. Los negocios no ingresan credenciales ni conectan su
numero en esta etapa. Cada owner verifica mediante OTP el WhatsApp receptor.

La conexion de un remitente propio por negocio queda reservada para una fase
posterior Growth/VIP mediante Embedded Signup y aislamiento por tenant.

## Orden de activacion

1. Ejecutar `docs/sql/20260726_owner_whatsapp_recipient_verification.sql`.
2. Crear y aprobar en Twilio:
   - plantilla Utility `owner_new_booking_v1` con variables `{{1}}` a `{{10}}`;
   - plantilla `whatsapp/authentication` `owner_phone_verification_v1` con el
     unico codigo `{{1}}` y boton `COPY_CODE`.
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
TWILIO_OWNER_PHONE_VERIFICATION_CONTENT_SID=HXxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

Puede usarse `TWILIO_MESSAGING_SERVICE_SID` en lugar de
`TWILIO_WHATSAPP_FROM_NUMBER` si el servicio ya contiene el remitente aprobado.
`TWILIO_STATUS_CALLBACK_URL` es opcional.

4. Desplegar backend despues del SQL.
5. Desplegar web y publicar el nuevo build movil.
6. En la pantalla `WhatsApp y mensajes`, solicitar el OTP, verificar el numero y
   activar el aviso de reservas.
7. Crear una reserva cliente de prueba y validar `notification_delivery`.

## Controles incluidos

- OTP aleatorio de seis digitos.
- Hash BCrypt, sin codigo en texto plano ni logs.
- Vigencia de 10 minutos.
- Espera de 60 segundos para reenvio.
- Maximo de 5 intentos.
- El numero del perfil cambia solo despues de verificar.
- Cambiar el telefono despues invalida la verificacion anterior.
- No se crea entrega WhatsApp para un receptor no verificado.
- IN_APP y PUSH permanecen como respaldo.