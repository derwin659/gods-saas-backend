# Asistente Super Gods por WhatsApp — piloto

Estado: implementacion local, desactivada por defecto; no desplegada ni probada con trafico real.

## Alcance

Cliente registrado y con telefono E.164 verificado: escribe `GODS CODIGO_NEGOCIO`, consulta sus puntos reales, promociones elegibles, disponibilidad y solicita una reserva. Antes de escribir en agenda, el backend muestra servicio, sede, profesional, fecha, hora, moneda e importe y exige `CONFIRMAR CODIGO`. `SALIR` cierra la sesion. La seleccion del negocio caduca tras 30 minutos de inactividad.

La IA interpreta el mensaje mediante Responses API y salida JSON estructurada. No recibe identidad del cliente ni saldo, no tiene acceso SQL ni puede confirmar operaciones. El backend fija identidad y negocio, valida IDs contra catalogo, reconsulta disponibilidad y cotiza con las reglas existentes de ClientBookingService. Los detalles de catalogo y el texto escrito por el cliente se envian al proveedor; no se envia el historial privado del cliente. Se usa `store:false` y no se registra texto, telefonos ni errores completos del proveedor en logs nuevos.

La verificacion entrante del owner sigue teniendo prioridad. El asistente solo procesa webhooks firmados enviados al numero central configurado. MessageSid evita repetir acciones; la sesion se bloquea en PostgreSQL por telefono. Cada intento atendido consume cuota persistente, incluso si falla el proveedor, para no reabrir gasto mediante reintentos.

## Planes y costo

Se mantienen los planes comerciales vigentes: Starter S/49.90, Growth S/99.90 y Pro S/149.90. El usuario menciono Pro de 99: la decision sobre renombrar sigue pendiente y no se alteraron pagos, productos IAP ni contratos.

Cuotas iniciales configurables para un piloto, NO promesa de rentabilidad:

| Plan interno | Intentos atendidos por mes |
|---|---:|
| STARTER | 100 |
| GROWTH / GODS_AI | 300 |
| PRO / PRO_LEGACY / ENTERPRISE | 600 |
| FREE / BASIC / STARTER_LEGACY / desconocido | 0 |

Una interaccion no equivale a una cita: una reserva puede requerir varias. La apertura de negocio tambien consume cuota. Los creditos visuales de IA permanecen separados. El consumo se agrega por negocio y mes calendario en su zona horaria; no por sede. Al agotar cuota el asistente no responde para evitar gasto repetido. Las consultas entrantes siguen pudiendo generar cargos del proveedor: la cuota no es un limite monetario sobre todo el trafico de Twilio. Los avisos transaccionales existentes tienen su propio consumo.

El costo real incluye mensajes entrantes y salientes de Twilio, cargos de Meta, tokens del modelo, infraestructura, impuestos y soporte. Medirlo en un piloto antes de publicar cantidades incluidas. No hay recargas, cobro por exceso ni ajuste automatico de planes en esta entrega.

## Activacion controlada

1. Aplicar `src/main/resources/db/manual/20261028_whatsapp_assistant.sql` en PostgreSQL. `ddl-auto` es none.
2. Configurar mediante variables/configuracion segura del servidor (nunca guardar valores secretos en Git):

```yaml
whatsapp:
  assistant:
    enabled: false
    tenant-ids: "" # IDs de negocios piloto separados por coma; vacio no habilita ninguno.
    openai-api-key: ${WHATSAPP_ASSISTANT_OPENAI_API_KEY:}
    model: ${WHATSAPP_ASSISTANT_MODEL:} # Modelo de texto compatible con Responses + JSON Schema estricto.
    starter-turns: 100
    growth-turns: 300
    pro-turns: 600
```

3. Mantener las propiedades Twilio existentes: auth-token, from-number, inbound-webhook-url y validate-inbound-signature=true. La URL firmada es la misma del webhook existente. No se requieren plantillas para este flujo de respuesta iniciado por el cliente; verificar las reglas y tarifas actuales de Meta antes de activarlo.
4. Elegir un negocio de prueba con suscripcion activa, numero cliente verificado y catalogo/precios configurados. Habilitar unicamente ese tenant y el flag. Probar puntos, promociones, sin disponibilidad, confirmacion repetida y cambio de precio. Configurar tambien alertas y limites de gasto en las cuentas de proveedores.
5. Desplegar el backend antes de la app; si el endpoint no existe la pantalla muestra estado no disponible sin bloquear las otras opciones.

No se han aplicado migraciones, creado recursos externos ni cambiado credenciales o configuracion de produccion. No se realizaron llamadas pagadas a OpenAI ni a Twilio durante las pruebas locales.

## API del dueño y app

`GET /api/owner/whatsapp-settings/assistant` usa el tenant de la sesion autenticada. Devuelve enabled, plan, monthlyTurns, usedTurns, remainingTurns y month. La pantalla existente WhatsApp y mensajes muestra estado y consumo; el dueño no puede activar el flag global ni ampliar su cuota desde el cliente.

## Limites del primer alcance

- Reservas de un servicio del catalogo por conversacion. Los paquetes existentes dependen de su contrato de catalogo.
- Negocios con adelanto y servicios de precio variable siguen reservando desde la app; no se evita el flujo de comprobante.
- Sin canje de puntos, cancelacion/reprogramacion conversacional, audios, campañas automaticas ni contactos sin cuenta verificada en este piloto.
- Starter conserva su regla existente de hasta 5 promociones cuando promotionsEnabled=false; el asistente consulta las promociones elegibles de ese negocio. No se habilitan campañas masivas adicionales.
- Cuotas, sesiones e idempotencia necesitan PostgreSQL; las pruebas unitarias no sustituyen el ensayo de la migracion y de concurrencia con esa base.
- Verificar concurrencia de reservas desde distintos canales contra las reglas actuales de agenda antes de abrir a todos los negocios.
- Limpiar registros de MessageSid solo despues de la ventana de reintentos (minimo 7 dias); definir retencion operativa de sesiones y metricas.

## Siguientes ampliaciones de producto

Starter: puntos, consultas, reservas y recordatorios existentes. Growth: mayor consumo y promociones existentes. Pro: futura reprogramacion, campañas con consentimiento, bandeja para el dueño y analisis de citas recuperadas; estas ampliaciones no estan incluidas en el codigo del piloto actual.

Referencia API: https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses

## Verificacion local

- 22 pruebas nuevas aprobadas: identidad y tenant, cuotas, confirmacion, cambios de precio, adelantos, firma/TwiML, SQL de idempotencia y concurrencia del consumo.
- La migracion se ejecuto durante pruebas en H2 con modo PostgreSQL. Se verifico que 25 solicitudes concurrentes solo consumen 10 cupos cuando el limite es 10 y que un rollback externo no devuelve el consumo del proveedor.
- Analisis estatico de la pantalla Flutter modificada sin incidencias.
- No se verifico PostgreSQL de produccion, la interpretacion con un modelo real ni la entrega de WhatsApp; activar un piloto requiere esas comprobaciones.
## Variables de entorno del servidor

application.yml y application-prod.yml ya incluyen la traduccion a variables de entorno. No pegar otra seccion whatsapp duplicada.

| Variable | Valor inicial |
|---|---|
| WHATSAPP_ASSISTANT_ENABLED | false |
| WHATSAPP_ASSISTANT_TENANT_IDS | IDs piloto separados por coma; dejar vacio deshabilita todos |
| WHATSAPP_ASSISTANT_OPENAI_API_KEY | Clave privada de API, guardar exclusivamente en el gestor de secretos/variables del servidor |
| WHATSAPP_ASSISTANT_MODEL | ID de modelo habilitado para la cuenta, compatible con Responses y JSON Schema estricto |
| WHATSAPP_ASSISTANT_STARTER_TURNS | 100 |
| WHATSAPP_ASSISTANT_GROWTH_TURNS | 300 |
| WHATSAPP_ASSISTANT_PRO_TURNS | 600 |

Los cupos tienen esos valores por defecto; no es necesario definir las tres variables si no cambian. Mantener ENABLED=false hasta que el codigo actualizado este desplegado y esten definidos clave, modelo y tenant piloto. Activar el flag es una operacion posterior del piloto. Una suscripcion de ChatGPT no reemplaza la configuracion de la API del proveedor. No enviar la clave por el chat ni guardarla en documentos.
PRO_LEGACY usa el mismo cupo configurable de PRO (600 por defecto), manteniendo precio y suscripcion originales. Sigue siendo obligatorio autorizar el tenant en whatsapp.assistant.tenant-ids y tener suscripcion activa.
