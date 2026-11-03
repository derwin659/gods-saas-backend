# WhatsApp: número compartido y número propio

La elección del dueño se guarda en `scheduleConfig`: `whatsappSenderMode` (`SHARED` o `BUSINESS`) y `whatsappBusinessPhone` (E.164). El teléfono escrito es una solicitud; solo una conexión aprobada y provisionada en el servidor puede activar `CONNECTED`. No se aceptan credenciales desde las pantallas de web o móvil ni se devuelven en sus respuestas.

## Activación por negocio

1. Completar el alta oficial mediante Tech Provider / Embedded Signup de Meta y Twilio. Cada negocio debe tener una WABA y una subcuenta Twilio dedicadas. Esta entrega permite un alta asistida por operaciones; no incluye todavía la interfaz de Embedded Signup autoservicio. Antes de migrar un teléfono existente, revisar con el dueño el impacto sobre su WhatsApp Business.
2. Confirmar en Twilio que el remitente está aprobado y operativo. Configurar el mismo webhook HTTPS de entrada usado por SuperGods y mantener `TWILIO_VALIDATE_INBOUND_SIGNATURE=true`.
3. Guardar las conexiones aprobadas en el secreto de servidor `TWILIO_WHATSAPP_BUSINESS_CONNECTIONS`, como un objeto JSON indexado por tenantId. Cada entrada debe tener `phone`, `accountSid`, `authToken` y `status` con valor `CONNECTED`. Los teléfonos y las subcuentas deben ser únicos y distintos del remitente/cuenta central. Un registro inválido o ambiguo no activa números propios.
4. Añadir `depositContentSid` cuando se utilicen avisos de adelantos fuera de la ventana de conversación y `bookingContentSid` si se requieren plantillas de reservas en esa subcuenta. Usar únicamente plantillas aprobadas para la WABA del negocio. No reutilizar Content SIDs de la cuenta central sin comprobar su disponibilidad en la subcuenta.
5. Aplicar `src/main/resources/db/manual/20261103_whatsapp_business_sender.sql` antes de desplegar. Es idempotente y solo amplía las tablas del asistente o de adelantos si existen. Las instalaciones nuevas ya incluyen las columnas en los scripts originales.
6. Desplegar el backend y la web y distribuir la versión móvil. Mantener Twilio y el asistente habilitados y el tenant en la lista de negocios autorizados; siguen aplicándose su suscripción, cuota y consentimiento.
7. El dueño selecciona Número de mi negocio, guarda el mismo teléfono aprobado y actualiza la configuración. El enlace/QR pasa al número propio solo cuando el servidor devuelve `CONNECTED`. Probar desde un teléfono cliente: alta con consentimiento, reserva, comprobante y aviso de adelanto si corresponde.

Esquema del secreto (reemplazar los marcadores con valores reales en el gestor de secretos, nunca en Git):

```json
{
  "TENANT_ID": {
    "phone": "+NUMERO_CON_CODIGO_DE_PAIS",
    "accountSid": "SID_DE_LA_SUBCUENTA",
    "authToken": "TOKEN_DE_LA_SUBCUENTA",
    "status": "CONNECTED",
    "bookingContentSid": "PLANTILLA_APROBADA_SI_CORRESPONDE",
    "depositContentSid": "PLANTILLA_APROBADA_SI_CORRESPONDE"
  }
}
```

## Aislamiento y comportamiento

- Por defecto y mientras el número propio esté pendiente, se mantiene el asistente compartido y su enlace/QR.
- Los webhooks se validan contra el token de la subcuenta asignada al número de destino y su AccountSid exacto. No se intenta otra cuenta cuando falla una firma. Un destino desconocido o duplicado se rechaza.
- El número propio queda vinculado exclusivamente a su negocio. No permite cambiar a otro negocio mediante un código o texto. Se conserva el modelo original de una conversación activa por teléfono cliente; cambiar de destino descarta el borrador y la confirmación anterior.
- Los adelantos guardan el remitente del chat de origen. Los avisos posteriores conservan esa subcuenta aunque se cambie la modalidad. Si se retira la conexión aprobada, no se sustituyen por mensajes de otra cuenta. Un comprobante debe llegar al mismo chat y descargarse desde la cuenta que le corresponde.
- Los avisos internos BOOKING_CREATED a usuarios verificados siguen en GODS Notificaciones. Los avisos a clientes con número propio activo fuerzan el From de ese negocio y no utilizan el pool global de remitentes.
- Para pausar nuevos chats propios, volver a SHARED. Para revocar también los envíos pendientes y las descargas, retirar la conexión del secreto. La rotación del token se hace en el servidor y en Twilio; no se solicitan credenciales al dueño en la app.
- Las pruebas no envían mensajes reales ni crean subcuentas o cargos. Falta una prueba piloto con una subcuenta aprobada antes de activar cada negocio en producción.

Fuentes oficiales:
- https://www.twilio.com/docs/whatsapp/isv/tech-provider-program/integration-guide
- https://www.twilio.com/docs/messaging/api/message-resource
