# Asistente conversacional Super Gods por WhatsApp

Estado: implementado y probado localmente. El despliegue y las pruebas con Twilio/OpenAI reales son pasos posteriores. No se han modificado credenciales ni configuracion de produccion desde esta tarea.

## Experiencia del cliente

- `Hola` descubre los negocios donde el telefono entrante esta registrado y verificado como cliente, tiene consentimiento transaccional y el tenant esta autorizado para el piloto.
- Si hay un negocio habilitado, abre su menu. Si hay varios, pregunta cual elegir mediante nombre o numero. No selecciona un negocio distinto por inferencia del modelo.
- Los numeros nuevos pueden elegir barberia por nombre o codigo y crear su ficha tras indicar nombre y responder ACEPTO. Las fichas existentes sin verificar no se reclaman por chat. Ver [registro y QR](whatsapp_onboarding.md).
- `GODS CODIGO` conserva el acceso directo. `CAMBIAR NEGOCIO` descarta el contexto anterior y permite buscar otra barberia habilitada por nombre o codigo. `SALIR` cierra la sesion.
- Menu: 1 Reservar, 2 Puntos, 3 Promociones, 4 Horarios, 5 Barberos disponibles, 6 Servicios y precios.
- El cliente puede escribir libremente o responder con el numero/nombre de una opcion. Se reconocen respuestas cortas como `mañana`, `cualquiera`, `el segundo` y `17:00` segun la pregunta previa.
- El backend conserva servicio, sede, profesional, fecha, hora y promocion. Cada paso pregunta solo por datos faltantes o invalidos. Los cambios de servicio o sede eliminan selecciones dependientes que deben revisarse.
- `MENU` conserva los datos elegidos pero invalida una confirmacion anterior. `NUEVA RESERVA` reinicia el borrador.

## Respuestas y reservas

Puntos y promociones: las consultas claras responden desde el backend sin depender de OpenAI. Otras formulaciones se interpretan mediante IA. Los puntos vienen de la cuenta de fidelizacion del cliente autenticado por su WhatsApp verificado. Las promociones se filtran por vigencia, tenant y elegibilidad; al seleccionar una oferta se valida de nuevo antes de reservar.

Servicios y precios: se listan desde el catalogo del negocio. Los servicios con precio variable requieren completar la reserva en la app. El modelo no suministra el precio final.

Horarios: se consulta ClientBookingService con sede, servicio, fecha y profesional opcional. Se puede filtrar desde una hora. Una consulta de disponibilidad nunca crea por si sola una cita.

Barberos disponibles: se filtran por sede y servicios asignados y se consulta la disponibilidad real de cada profesional (maximo 20 por respuesta). Listar al equipo no afirma que todos esten libres.

Reserva: se muestra servicio, sede, profesional o cualquiera disponible, fecha, hora, promocion y precio calculado por las reglas existentes. Solo se crea la cita despues de `SI` en el paso de confirmacion o `CONFIRMAR CODIGO` valido. `NO` descarta el borrador. El backend revalida seleccion, precio y disponibilidad antes de crear la cita. Si cambia el precio, pide una nueva confirmacion.

Los adelantos por Yape pueden completarse por WhatsApp al activar el modulo de comprobantes; ver [Adelantos por WhatsApp](whatsapp_deposits.md). Si el modulo esta apagado o no hay Yape valido, se mantiene el flujo de app para completar comprobantes. Canje de puntos, cancelacion/reprogramacion de citas existentes y solicitudes fuera del alcance se remiten a la app o al negocio. No se inventan respuestas de temas no presentes en el catalogo.

## Memoria, seguridad y recuperacion

La memoria se guarda dentro del campo `draft` de whatsapp_assistant_session, junto al objeto `_assistant` con modo, paso, opciones y ultima pregunta. No requiere nuevas columnas ni una migracion adicional a 20261028_whatsapp_assistant.sql. Los borradores antiguos siguen siendo compatibles.

La sesion caduca tras 30 minutos de inactividad. Se conserva la validacion de firma de Twilio y del numero destinatario central, la identidad del cliente, suscripcion activa, lista de tenants autorizados y cuota mensual. MessageSid evita ejecutar de nuevo un mensaje repetido y la sesion se bloquea por telefono.

El modelo solo interpreta intenciones y campos; no tiene acceso SQL, no elige identidad o tenant y no ejecuta confirmaciones. El contexto enviado incluye IDs/nombres de catalogo y el borrador con su ultima pregunta; no incluye fotos del catalogo ni historial privado de visitas. Se usa store=false. No se registran claves, texto del cliente o cuerpos de error en los nuevos logs.

Ante timeout o error de interpretacion se informa al cliente y se muestran las opciones guardadas para continuar por numero. Las rutas de menu y las respuestas cortas reconocidas funcionan sin una llamada al modelo. Las frases libres siguen necesitando una llamada exitosa al proveedor y deben validarse en el piloto.

La espera de lectura del proveedor es 10 segundos por defecto, configurable entre 1000 y 10000 ms. No se hacen reintentos automaticos dentro del webhook. Si la latencia total sigue superando el tiempo del webhook, sera necesario un flujo asincrono con envio saliente Twilio; esta entrega no implementa esa cola.

## Planes y consumo

Se mantienen precios y productos de suscripcion actuales: Starter S/49.90, Growth S/99.90 y Pro S/149.90. No se renombro Growth a Pro ni se cambiaron contratos.

| Plan interno | Intentos atendidos por negocio y mes |
|---|---:|
| STARTER | 100 |
| GROWTH / GODS_AI | 300 |
| PRO / PRO_LEGACY / ENTERPRISE | 600 |
| FREE / BASIC / STARTER_LEGACY / desconocido | 0 |

PRO_LEGACY conserva precio y suscripcion originales y comparte el cupo configurable de PRO. Siempre se necesita autorizar el tenant.

Una interaccion no equivale a una reserva: cada respuesta atendida consume cuota; los intentos fallidos del proveedor tambien la consumen. El mes se calcula en la zona horaria del negocio. Los creditos visuales son independientes. Al agotarse el cupo el asistente queda en silencio para evitar respuestas pagadas repetidas.

Estos cupos son de piloto y no garantizan rentabilidad. Los mensajes entrantes siguen pudiendo generar cargos de Twilio y no estan bloqueados por esta cuota. Los avisos transaccionales existentes se facturan aparte de este contador. El costo total incluye Twilio, Meta, tokens del modelo, infraestructura, impuestos y soporte. No hay cobro automatico por exceso ni recargas implementadas.

Starter mantiene su regla existente de hasta 5 promociones si promotionsEnabled=false. Esta entrega no agrega campañas masivas ni modifica el catalogo de planes.

## Variables del servidor

application.yml y application-prod.yml ya enlazan estas variables. No pegar una segunda seccion whatsapp en YAML.

| Variable | Valor inicial |
|---|---|
| WHATSAPP_ASSISTANT_ENABLED | false hasta activar el piloto |
| WHATSAPP_ASSISTANT_TENANT_IDS | IDs numericos separados por coma; vacio no habilita ninguno |
| WHATSAPP_ASSISTANT_OPENAI_API_KEY | Guardar solo en el gestor seguro de variables del servidor |
| WHATSAPP_ASSISTANT_MODEL | Modelo habilitado en la cuenta con Responses y JSON Schema estricto |
| WHATSAPP_ASSISTANT_READ_TIMEOUT_MS | 10000 |
| WHATSAPP_ASSISTANT_STARTER_TURNS | 100 |
| WHATSAPP_ASSISTANT_GROWTH_TURNS | 300 |
| WHATSAPP_ASSISTANT_PRO_TURNS | 600 |

Los valores de cupos y timeout son opcionales si se conservan los predeterminados. Mantener configurados TWILIO_WHATSAPP_FROM_NUMBER, TWILIO_AUTH_TOKEN, TWILIO_INBOUND_WEBHOOK_URL y la validacion de firma. La verificacion de WhatsApp del owner sigue teniendo prioridad en el webhook. No enviar claves por chat ni guardarlas en Git.

## Comprobar el despliegue

1. Si ya se aplico 20261028_whatsapp_assistant.sql, no ejecutar otra migracion por esta ampliacion.
2. Subir y desplegar el codigo actualizado del backend; aplicar variables en Railway no sube archivos locales.
3. Esperar a que el nuevo despliegue quede activo. Los logs de una revision anterior no verifican la version nueva.
4. Escribir `HOLA` desde un cliente verificado. Debe aparecer el menu numerado de seis opciones; esa es una diferencia visible respecto al saludo anterior.
5. Probar puntos, promociones, seleccion de profesional, horarios sin disponibilidad y una reserva completa. Confirmar el resultado en la agenda.
6. Probar cambio de precio y confirmacion repetida con datos controlados. Revisar consumo y gastos del proveedor antes de abrir el piloto a mas tenants.

Ejemplo de prueba guiada sin IA: `HOLA` → `1` → numero de servicio → sede/profesional si se solicitan → `mañana` → numero de horario → `SI`. Probar tambien texto libre con el modelo real; una prueba guiada no confirma conectividad o calidad de interpretacion de OpenAI.

## Estado para el dueño

GET /api/owner/whatsapp-settings/assistant utiliza el tenant de la sesion autenticada y devuelve enabled, plan, monthlyTurns, usedTurns, remainingTurns y month. La pantalla WhatsApp y mensajes muestra estado, consumo y aviso al 80 %. No permite ampliar cuota ni activar tenants desde el cliente.

## Verificacion local

Las pruebas incluyen firma y XML del webhook, identidad/tenant/consentimiento, cuotas y concurrencia, idempotencia, consultas directas con timeout simulado, seleccion explicita de negocio, memoria entre turnos, menu, datos faltantes, cambios de precio, rechazo de reserva, disponibilidad y profesionales con agenda real simulada.

Se prueba ademas una conversacion completa con persistencia SQL en H2 modo PostgreSQL: cada mensaje lee la sesion guardada y una confirmacion repetida no crea dos citas. Esto no sustituye una prueba en PostgreSQL real ni una prueba pagada de entrega Twilio/OpenAI.

Comando: gradlew.bat test --tests '*WhatsappAssistant*' --tests '*TwilioWhatsappAssistantWebhookTest' --offline --console=plain.

Operacion: conservar MessageSid al menos durante la ventana de reintentos (minimo 7 dias) y definir retencion de sesiones y metricas. Verificar tambien concurrencia de reservas desde otros canales antes de escalar.

Documentacion oficial de la API utilizada: https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses

## Diagnostico de mensajes sin respuesta

El saludo, menu y consultas directas no requieren credenciales de IA; aiConfigured se informa aparte en el endpoint de estado. El acceso sigue sujeto al flag, destinatario, identidad y cuota. El log entrante incluye dialogueVersion=2. `WhatsApp assistant skipped` explica las omisiones con codigos seguros: disabled, destination_mismatch, invalid_sender, invalid_message, duplicate_message, rate_limited o monthly_quota_exhausted. `WhatsApp assistant result: replyPresent=true` indica que el backend genero texto; no confirma entrega de Twilio. Si no aparece un log entrante nuevo, revisar recepcion/webhook antes de cambiar la IA.