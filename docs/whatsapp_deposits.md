# Adelantos por Yape dentro de WhatsApp

Estado: implementacion local. Requiere desplegar el backend, aplicar la migracion nueva y habilitar el modulo para probarlo con Twilio/Cloudinary reales. No se realizaron cargos, envios ni cambios de produccion durante las pruebas.

## Configuracion del negocio

Reutiliza la pantalla existente de configuracion de reservas y pago inicial. No se agrega una cuenta de Yape de Super Gods: cada negocio cobra en su propio Yape.

1. Activar el pago inicial y elegir monto fijo o porcentaje.
2. Agregar o activar Yape en los metodos de pago.
3. Indicar numero de Yape en accountValue; usar instrucciones para el titular y cualquier indicacion que deba leer el cliente. Tambien puede guardarse el QR en qrImageUrl.
4. El metodo debe estar activo y ser global al negocio o pertenecer a la sede elegida. Si hay un Yape por sede tiene preferencia sobre el global.
5. Si el metodo requiere numero de operacion, el cliente debe proporcionarlo; la foto por si sola no lo reemplaza.

## Flujo del cliente

- Selecciona servicio, sede, profesional y horario por chat.
- Recibe total y adelanto calculados desde la configuracion real. No se muestran instrucciones para pagar antes de confirmar el resumen.
- Al responder SI, se crea un apartado en la agenda como PENDING_DEPOSIT_VALIDATION con depositStatus=AWAITING_EVIDENCE. Aun no es una cita confirmada ni un pago aprobado.
- Recibe los datos de Yape del negocio y el plazo limite (15 minutos por defecto, o hasta la hora de la cita si es antes).
- Envia una foto JPG, PNG o WEBP de hasta 5 MB. Puede incluir el numero de operacion en el texto de la foto o enviarlo despues: OPERACION 123456.
- El webhook responde rapido y pone la foto en una cola persistente. Un trabajador la descarga con autenticacion de Twilio, verifica el formato mediante el almacenamiento existente y la guarda en Cloudinary.
- La URL del comprobante y la referencia se vinculan a la misma cita de la agenda. Si falta referencia queda AWAITING_REFERENCE y el bot la pide; si todo esta listo pasa a PENDING_VALIDATION.
- El dueño revisa el comprobante y el ingreso real desde la agenda. Aprobar cambia a PAID y a CONFIRMED/RESERVADO segun la ruta existente. Rechazar cambia a REJECTED/DEPOSIT_REJECTED y permite enviar otro comprobante, sin pedir otro pago automatico.
- Cada aprobacion o rechazo genera un aviso persistente para el cliente. ESTADO permite consultar el ultimo adelanto aun si caduco la sesion conversacional.

Si no hay Yape valido, el modulo esta apagado o el servicio tiene precio variable, se mantiene el flujo de app/contacto. El QR se comparte como enlace del metodo configurado, no se analiza ni se usa para ejecutar pagos.

## Plazo y estados

AWAITING_EVIDENCE vence y libera el horario si no llega evidencia. REJECTED abre otro plazo para corregir el comprobante. Si la imagen esta procesandose no se cancela en mitad de la transferencia. Tras recibirla y pasar a AWAITING_REFERENCE/PENDING_VALIDATION no se cancela automaticamente un posible pago: el dueño debe revisarlo.

Se permite un solo adelanto abierto por telefono en esta primera version. Una solicitud adicional informa la reserva pendiente para evitar pagos duplicados.

Los apartados y las reservas creadas por ClientBookingService se serializan por tenant antes de revisar disponibilidad. Los flujos de modificacion/creacion desde otros canales conservan sus reglas existentes; probar concurrencia con agenda owner y reprogramaciones en PostgreSQL antes de escalar el piloto.

## Activacion en servidor

1. Aplicar **src/main/resources/db/manual/20261028_whatsapp_assistant_deposits.sql**. Es adicional a la migracion de sesiones aplicada anteriormente. Crea tres tablas; no cambia precios ni saldos.
2. Desplegar el backend actualizado.
3. Mantener la configuracion existente de WhatsApp/Twilio, Cloudinary y la lista de tenants piloto.
4. Agregar a Railway:

```text
WHATSAPP_ASSISTANT_DEPOSITS_ENABLED=true
WHATSAPP_ASSISTANT_DEPOSIT_HOLD_MINUTES=15
```

El flag es false por defecto; los 15 minutos tambien tienen valor por defecto y se limitan a 5–60. No activar el flag antes de aplicar la migracion.

## Mensajes de aprobacion y rechazo

Los avisos usan el numero central de Twilio y el consentimiento transaccional del cliente, revisando de nuevo cuenta, telefono verificado y tenant. Se envian desde una cola despues de confirmar la transaccion de base de datos. Un fallo de WhatsApp no revierte la aprobacion del pago.

Dentro de la ventana de atencion se envia texto. El emisor usa un margen conservador de 23 horas desde el ultimo mensaje de pago registrado. Para avisos posteriores necesita una plantilla Utility aprobada en Twilio:

```text
WHATSAPP_ASSISTANT_DEPOSIT_STATUS_CONTENT_SID=HX...
```

Contrato de plantilla: un parametro de texto `1` con el resumen transaccional. Ejemplo de cuerpo a solicitar para aprobacion: `Actualizacion de tu reserva en Super Gods: {{1}}`. La aprobacion depende del proveedor; no se creo ni publico una plantilla automaticamente. Sin una plantilla configurada, los avisos fuera del margen se marcan FAILED con DEPOSIT_TEMPLATE_REQUIRED y el dueño debe contactar al cliente/revisar su configuracion.

La API usa los valores TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN y TWILIO_WHATSAPP_FROM_NUMBER existentes. No se deben pegar claves en Git o en documentos.

## Colas, entrega y recuperacion

- Media PENDING -> PROCESSING -> DONE/FAILED. Se conserva URL almacenada y referencia por intento para trazabilidad.
- Avisos PENDING -> PROCESSING -> SENT/FAILED/SKIPPED. SENT significa aceptado por Twilio; provider_sid permite consultar entrega real en Twilio, no equivale a DELIVERED.
- Tras 10 minutos, una descarga interrumpida se marca fallida y se informa al cliente. Un envio interrumpido se marca UNKNOWN, sin reenvio automatico: puede haber sido aceptado antes del fallo.
- Ante FAILED/UNKNOWN reconciliar el SID/log de Twilio antes de reencolar manualmente. No reintentar a ciegas para no duplicar mensajes.
- Las fotos se descargan solo desde el endpoint de medios de la cuenta y MessageSid autenticados. Los redireccionamientos se limitan a HTTPS y dominios de proveedores permitidos y no reenvian la credencial inicial a los destinos.
- Los comprobantes se guardan con el mecanismo de Cloudinary ya usado por la app. Definir retencion/acceso conforme a la politica del negocio; no enviarlos al modelo de IA.
- El estado y el comprobante son visibles al consultar/refrescar la agenda existente; no se agrego un canal de refresco en tiempo real a la app.

## Costos y limites

Las capturas y respuestas transaccionales consumen mensajes Twilio y almacenamiento Cloudinary. No se cobran recargas ni se ajustan suscripciones automaticamente. Las respuestas de un adelanto ya iniciado siguen procesandose aunque se agote el cupo conversacional, para no bloquear el comprobante de un cliente que ya pago. Monitorizar ese consumo aparte antes de venderlo como paquete fijo.

No hay OCR de importes, acceso a la cuenta Yape, validacion bancaria automatica ni movimiento de dinero. La aprobacion corresponde al dueño y no se infiere de una imagen.

## Prueba de piloto

Configurar un negocio autorizado con Yape y un adelanto pequeño de prueba. Elegir un horario controlado, confirmar el resumen, verificar el apartado en agenda y enviar una captura con referencia. Comprobar que la imagen aparezca en la misma cita, aprobar desde la agenda y verificar el aviso en WhatsApp/Twilio. Repetir con rechazo, falta de referencia, otra imagen, plazo vencido y foto demasiado grande. Confirmar que ninguna foto o mensaje repetido apruebe el pago por si solo.

Las pruebas locales usan SQL en H2 modo PostgreSQL y dobles de Twilio/Cloudinary; no confirman disponibilidad de credenciales, aprobacion de plantilla ni entrega real del proveedor.

Referencias del proveedor: https://www.twilio.com/docs/whatsapp/tutorial/send-and-receive-media-messages-whatsapp-nodejs y https://www.twilio.com/docs/whatsapp/key-concepts.
