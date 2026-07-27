# Plantilla WhatsApp: owner_phone_verification_v1

Esta plantilla verifica que el usuario controla el numero donde desea recibir
alertas operativas de GODS. El codigo dura 10 minutos, se almacena unicamente
como hash BCrypt y se invalida despues de 5 intentos fallidos.

## Twilio WhatsApp

Crearla en `Messaging > Content Template Builder` con:

- Friendly name: `owner_phone_verification_v1`.
- Idioma: Espanol.
- Content type: `whatsapp/authentication`.
- Accion: `COPY_CODE`.
- Recomendacion de seguridad: activa.
- Expiracion visible: 10 minutos.

El cuerpo de una plantilla de autenticacion es definido por WhatsApp. Al enviar,
el backend proporciona solamente la variable `{{1}}` con el OTP.

Copiar el Content SID `HX...` aprobado y configurar:

```text
TWILIO_OWNER_PHONE_VERIFICATION_CONTENT_SID=HXxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

## Meta Cloud API

- Nombre: `owner_phone_verification_v1`.
- Categoria: `AUTHENTICATION`.
- Idioma: Espanol (`es`).
- Tipo de boton: copiar codigo.
- Expiracion: 10 minutos.

## Seguridad

- El endpoint requiere sesion owner/admin/barber autenticada y solo verifica al usuario de la sesion.
- Se normaliza el numero a E.164 segun el pais del tenant.
- Nunca se registra el OTP en logs ni se guarda en texto plano.
- Existe espera de 60 segundos entre envios.
- El numero pendiente reemplaza `app_user.phone` solo despues de verificar.
- Si el telefono cambia posteriormente, la verificacion deja de ser valida.
- Las reservas no generan entrega WhatsApp para usuarios sin numero verificado.