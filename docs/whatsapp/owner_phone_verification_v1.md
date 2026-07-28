# owner_phone_verification_v1: no usar

La plantilla Authentication fue rechazada por Meta con:

```text
OAuthException code=10 subCode=2388185
This WhatsApp business account does not have permission to create message template
```

El rechazo corresponde a elegibilidad/permisos de la WABA nueva, no al texto.
No configurar su Content SID y no crear una `v2` mientras la cuenta no sea
elegible.

El flujo vigente es iniciado por el owner. GODS genera el mensaje
`VERIFICAR GODS <usuario> <codigo>` y abre el numero central. Twilio entrega el
mensaje al webhook firmado; el backend valida numero, codigo, vigencia e
intentos. La configuracion completa esta en
`docs/whatsapp/gods_central_notifications_setup.md`.
