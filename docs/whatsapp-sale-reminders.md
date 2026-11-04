# WhatsApp al aprobar ventas y recordatorios de una hora

## Comportamiento

- La aprobacion de una venta pendiente mediante CASH_APPROVE_SALES (dueno o administrador autorizado) registra SALE_RECEIPT en la misma transaccion. La venta ya aprobada al crearla conserva su evento existente.
- La red no se llama durante la aprobacion: la cola se procesa despues de confirmar la transaccion.
- Repetir la aprobacion no vuelve a emitir puntos ni comprobante; se bloquea la fila de venta durante la validacion.
- Con numero propio conectado, la respuesta customerWhatsappAutomatic=true impide que web y movil reconstruyan un enlace wa.me para enviar desde la sesion personal. No significa que Twilio haya entregado el mensaje. Clientes anteriores tampoco reciben el texto manual en esa respuesta.
- La opcion Mensaje al aprobar una venta controla el envio; requiere cliente con consentimiento transaccional, telefono valido y el plan que ya habilita WhatsApp (GROWTH o superior).
- Los mensajes al cliente usan la subcuenta, credencial, numero y plantilla del negocio. Una seleccion BUSINESS sin conexion no envia con otro remitente.
- La cola conserva el control existente de consentimiento y baja del cliente. Una plantilla ausente genera FAILED, sin intento de texto libre ni reintento automatico.
- Los avisos internos de nuevas reservas al dueno/equipo siguen con su remitente central existente.

## Recordatorios

El scheduler comprueba cada minuto la hora local de cada negocio, incluidas citas que cruzan medianoche. Encola el recordatorio entre 60 y 55 minutos antes (ventana para tolerar retrasos del scheduler). No garantiza un envio al segundo exacto.

Se deduplica por negocio, cliente, cita, tipo y fecha/hora de inicio. La fila de cita se bloquea al encolar y se vuelve a comprobar su horario. Si se reprograma, el horario nuevo permite un recordatorio nuevo. Al despachar se verifica nuevamente que la cita siga vigente, que conserve su cliente/horario y que aun este en plazo. Citas canceladas, atendidas, completadas o con adelanto requerido no validado no reciben recordatorio.

El recordatorio de 30 minutos se conserva solo para push/in-app; no agrega otro WhatsApp. El de 24 horas mantiene su configuracion previa y queda fuera de estas dos nuevas plantillas.

Las filas de entrega pendientes se bloquean al despachar para evitar que workers concurrentes envien la misma fila. Si el servidor cae despues de que Twilio acepto un mensaje y antes del commit, se debe reconciliar con Twilio antes de volver a enviarlo: no se promete entrega exactamente una vez ante fallos de red/proceso.

## Publicacion pendiente

1. Aplicar primero las migraciones previas de Embedded Signup, despues src/main/resources/db/manual/20261103_whatsapp_sale_reminders.sql y src/main/resources/db/manual/20261103_whatsapp_post_sale_parity.sql.
2. Publicar el backend y las actualizaciones de web/movil. Estos cambios locales no activan ni despliegan la conexion de Meta/Twilio.
3. Completar aprobaciones y conectar el numero del negocio.
4. Crear/aprobar las siguientes plantillas en la subcuenta de cada negocio, segun los requisitos de WhatsApp. No reutilizar un HX de la cuenta principal en otras subcuentas.
5. En whatsapp_business_connection del negocio configurar sale_receipt_content_sid y reminder60_content_sid. En la provision manual privada TWILIO_WHATSAPP_BUSINESS_CONNECTIONS se llaman saleReceiptContentSid y reminder60ContentSid. Nunca incluir tokens en documentos o clientes.
6. Activar los switches postventa/60 minutos, comprobar consentimiento y ejecutar una prueba real de venta y cita en un negocio piloto. No se enviaron mensajes reales ni se crearon recursos pagados durante las pruebas locales.

## Contrato de las plantillas

### Postventa completa (version 2)

Sustituye la plantilla corta de venta/total por el mismo mensaje del modo manual. Un generador compartido compone ambos: saludo, puntos anteriores, ganados y posteriores, descarga de app, codigo/instrucciones, reserva web y asistente, segun opciones habilitadas.

Al aprobar/crear una venta aprobada se guarda whatsapp_receipt_snapshot en sale. Los puntos se obtienen del movimiento EARN/SALE de esa venta (saldo_resultante - puntos, puntos, saldo_resultante), no del saldo del cliente al despachar. El mensaje no cambia por compras o canjes posteriores. Una venta sin puntos conserva el saldo y muestra ganancia 0. La notificacion guarda whatsapp_template_snapshot completo; su vista previa de 500 caracteres no recorta el mensaje automatico.

Textos listos para copiar: docs/whatsapp-postventa-templates/*.txt. Cada JSON acompana cuerpo, ejemplos, nombre, categoria y clave variant. Para el mensaje con todos los bloques usa points_app_code_booking_assistant.txt y nombre supergods_postventa_points_app_code_booking_assistant_v2. No usar el SID de la plantilla corta anterior.

Variables de la version completa: 1=cliente, 2=negocio, 3=puntos antes, 4=puntos ganados, 5=puntos despues, 6=URL app, 7=codigo negocio, 8=URL reserva, 9=activacion RESERVAR o GODS codigo, 10=URL asistente. Al omitir bloques cambia la numeracion: copiar siempre el JSON de la variante que se va a aprobar.

WhatsApp no permite saltos de linea dentro de variables. Por eso cada combinacion de bloques tiene su propia plantilla estatica y SID. Guardar solo las variantes utilizadas por el negocio en sale_receipt_content_sids (JSONB), por ejemplo {"points_app_code_booking_assistant":"HX...","points_app_code_booking":"HX..."}. En la provision manual privada se llama saleReceiptContentSids. El campo anterior sale_receipt_content_sid solo admite la plantilla completa version 2 con todos los bloques. Una variante sin SID falla explicitamente; no se envia una plantilla de contenido distinto.

Las plantillas con invitaciones de descarga/nueva reserva/asistente se solicitan como MARKETING; requieren consentimiento transaccional y de marketing vigentes. No se convierten automaticamente en mensajes de utilidad ni se agregan enlaces con variables de texto libre. La variante de solo puntos puede solicitarse como UTILITY, sujeto a la decision de Meta. Si el cliente no autoriza marketing, se omite el envio completo en lugar de enviar una version diferente. No modifica ni solicita automaticamente su consentimiento.

No aplicar el nuevo backend antes de la migracion adicional. Los pendientes antiguos sin snapshot no se rellenan con saldos actuales ni se envian con una plantilla distinta; requieren revision antes de reenvio.

### Recordatorio (reminder60_content_sid)

Variables: 1=nombre cliente, 2=negocio, 3=servicio, 4=fecha dd/MM/yyyy, 5=hora HH:mm.

Hola {{1}}, te recordamos tu cita en {{2}}.
Servicio: {{3}}.
Fecha: {{4}}.
Hora: {{5}}.
Tu cita es en aproximadamente una hora.

La plantilla supergods_reserva_confirmada_v1 creada para la revision confirma una reserva; no es una aprobacion de venta ni un recordatorio. No asignarla a estos campos sin adaptar el texto y obtener la aprobacion correspondiente.
