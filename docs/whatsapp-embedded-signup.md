# Alta guiada de WhatsApp en web y móvil

## Qué hace el dueño

En WhatsApp y mensajes selecciona Número de mi negocio, ingresa su teléfono y pulsa Conectar con Meta. Se abre una página de SuperGods con un enlace temporal. Desde ella el dueño abre la ventana oficial de Meta, inicia sesión y autoriza su negocio y número. Las contraseñas y los códigos de verificación del teléfono se ingresan exclusivamente en Meta.

SuperGods comprueba en el servidor que el código OAuth permita acceder al número e ID seleccionados dentro de la WABA autorizada. Después crea una subcuenta Twilio, registra su remitente y configura el webhook. Solo muestra Conectado cuando Twilio devuelve ONLINE para la misma cuenta, teléfono, WABA y remitente.

La web abre una nueva pestaña. El móvil usa el navegador externo y comprueba el estado al volver a la app. También existe Comprobar conexión. No hace falta entrar manualmente a la consola de Twilio para cada negocio.

## Configuración inicial de SuperGods — una sola vez

1. Completar el programa Tech Provider de Meta/Twilio, la vinculación con la Partner Solution de Twilio, la revisión de permisos y la verificación de acceso. Configurar Embedded Signup V4 / Facebook Login for Business. Estas aprobaciones externas no las crea el código.
2. En Meta, autorizar los dominios de la web donde se abrirá `/whatsapp/connect`, la configuración OAuth y los permisos `whatsapp_business_management` y `whatsapp_business_messaging`. La aplicación debe tener los accesos y modo adecuados para dar de alta negocios externos.
3. Añadir en el backend:

| Variable | Valor |
|---|---|
| `WHATSAPP_ONBOARDING_ENABLED` | `true` cuando la configuración y aprobaciones estén listas |
| `WHATSAPP_META_APP_ID` | App ID de Meta |
| `WHATSAPP_META_APP_SECRET` | App Secret, solo en el servidor |
| `WHATSAPP_META_CONFIG_ID` | Configuration ID de Facebook Login for Business / Embedded Signup V4 |
| `WHATSAPP_META_SOLUTION_ID` | Partner Solution ID de Twilio |
| `WHATSAPP_META_GRAPH_VERSION` | Versión soportada por la aplicación, predeterminada `v24.0` |
| `WHATSAPP_ONBOARDING_WEB_URL` | Origen HTTPS de la web, sin ruta; por ejemplo `https://supergodsapp.com` |
| `WHATSAPP_CREDENTIALS_ENCRYPTION_KEY` | 32 bytes aleatorios codificados en Base64; secreto independiente del JWT |

Mantener las variables existentes `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_INBOUND_WEBHOOK_URL`, `TWILIO_VALIDATE_INBOUND_SIGNATURE=true` y `WHATSAPP_TWILIO_ENABLED=true`. La URL entrante debe ser HTTPS y coincidir exactamente con el webhook utilizado para validar firmas.

La clave de cifrado debe guardarse y respaldarse en el gestor de secretos. Cambiarla sin migrar los datos impide descifrar las conexiones ya dadas de alta. No ponerla en Git ni compartirla en el chat. Puede generarse localmente con Node: `node -e "process.stdout.write(require('node:crypto').randomBytes(32).toString('base64'))"`.

4. Aplicar las migraciones `20261103_whatsapp_business_sender.sql` y `20261103_whatsapp_embedded_signup.sql` antes de desplegar. La segunda crea conexiones y sesiones; las credenciales se cifran con AES-256-GCM y las sesiones guardan únicamente hashes de sus tickets y capacidades.
5. Desplegar backend y web y distribuir la app móvil. Si se usa otro dominio, añadirlo a los orígenes CORS permitidos y a la configuración de Meta. Vercel debe servir `/whatsapp/connect` mediante la reescritura habitual de la SPA.
6. Hacer una prueba piloto real con una cartera de negocio distinta de la usada para crear la app Meta, tal como recomienda Twilio. Las pruebas automatizadas no crean subcuentas reales ni generan cargos.

## Seguridad, reintentos y soporte

- Inicio: `POST /api/owner/whatsapp-settings/onboarding/start`, solo para OWNER actual del tenant del JWT. Los enlaces duran cinco minutos y se canjean una vez; la capacidad resultante solo permite completar o consultar esa conexión durante treinta minutos.
- El JWT de SuperGods no viaja en la URL ni al navegador externo. El ticket se transporta en el fragmento y se elimina al abrir la página. La capacidad queda en memoria y usa `X-Whatsapp-Onboarding-Token`, sin permisos generales de la app.
- La sesión conserva el tenant, dueño y teléfono de origen. Se vuelve a comprobar el rol OWNER antes de crear recursos. No se aceptan tenantId, credenciales Twilio ni estado ONLINE enviados por el navegador.
- Se rechazan mensajes postMessage de orígenes que no sean exactamente los dominios oficiales de Meta admitidos. El servidor comprueba igualmente el permiso real de Meta sobre el número; un FINISH del navegador no basta.
- Se confirma en una transacción independiente la intención de crear recursos antes de llamar al proveedor. Si la respuesta de creación es incierta, se usa REVIEW_REQUIRED y no se repite automáticamente la operación. Operaciones debe revisar los recursos en Twilio antes de resolver ese estado. No borrar la fila para intentar de nuevo, porque podría duplicar una subcuenta ya creada.
- Las conexiones automáticas se suman a las conexiones manuales existentes. Las entradas manuales del secreto tienen prioridad para su tenant; se rechazan colisiones entre negocios.
- Los números o WABAs ya registrados requieren un proceso de migración adecuado. El alta guiada no elimina cuentas de WhatsApp ni garantiza la coexistencia con la aplicación WhatsApp Business; el dueño debe revisar las condiciones de Meta antes de aceptar un cambio.
- Los avisos fuera de la ventana de conversación siguen necesitando plantillas aprobadas de su subcuenta. El registro del remitente no aprueba plantillas automáticamente. Las plantillas de adelanto/reserva pueden asignarse en la conexión del servidor cuando corresponda.
- Al retirar o revocar una conexión hay que respetar los adelantos y avisos que conservan su remitente de origen; no sustituirlos por otro negocio.
- Se pueden depurar periódicamente las sesiones expiradas. No se eliminan automáticamente conexiones ni recursos de Twilio.

## Activación del asistente elegida para números propios

El asistente comienza solo con `RESERVAR`, `MENÚ` (también MENU) o el mensaje preparado del enlace/QR. Los enlaces propios preparan `RESERVAR`; se mantienen los códigos GODS antiguos válidos para ese negocio. Los mensajes normales no inician el bot, no consumen cuota de IA y no descartan el borrador de otra conversación. Una vez iniciado, se atienden sus respuestas hasta SALIR o hasta que venza la sesión. El número compartido conserva su comportamiento actual.

Fuentes oficiales:
- https://www.twilio.com/docs/whatsapp/isv/tech-provider-program/integration-guide
- https://www.twilio.com/docs/whatsapp/api/senders
- https://www.postman.com/meta/whatsapp-business-platform/
