# Menu y canjes de premios en WhatsApp

## Opciones del cliente

1. Reservar una cita.
2. Consultar puntos.
3. Ver promociones vigentes y elegibles para su cuenta.
4. Consultar horarios por servicio/sede/dia.
5. Consultar barberos disponibles: explica primero que necesita servicio y dia, y luego lista profesionales con horarios reales. No afirma disponibilidad sin consultarla.
6. Servicios y precios (desde el valor registrado para servicios variables).
7. Ver y canjear premios.
8. Mis canjes: recuperar codigos y consultar si estan pendientes de entrega o ya usados.
9. Mis proximas reservas del negocio, con fecha, hora, servicio, profesional, estado y estado de adelanto cuando corresponde.

MENU vuelve al menu; CAMBIAR BARBERIA cambia de negocio; SALIR cierra la sesion. Los menus de clientes existentes y nuevos utilizan el mismo texto.

## Correcciones de respuestas

- Las opciones comunes y numeradas se resuelven sin depender de OpenAI ni del tamano del contexto del modelo.
- La opcion 3 consulta promociones sin cargar el catalogo de reservas. Si no hay promociones elegibles lo explica y permite continuar.
- El listado de promociones muestra cada oferta una sola vez, con descripcion resumida. El cliente puede elegir el numero.
- El webhook divide respuestas largas en bloques de como maximo 1400 unidades UTF-16. Conserva emojis y escapa XML en cada bloque. Esto evita enviar un unico mensaje que exceda el limite de Twilio (1600 caracteres). Las partes pueden generar varios mensajes facturables por Twilio; siguen siendo una solicitud del cupo del asistente.
- Un mensaje enviado dentro del intervalo de proteccion de dos segundos recibe una instruccion para volver a enviarlo. No ejecuta una accion.
- Al agotarse el cupo se informa al cliente una vez por mes/contexto, en lugar de fallar silenciosamente desde el primer intento. Las repeticiones del aviso se suprimen para no generar respuestas de pago ilimitadas.
- Logs de resultado incluyen replyPresent, replyLength y dialogueVersion=3, sin texto del cliente, claves ni telefonos. La presencia de respuesta en el backend no confirma entrega en WhatsApp: comprobar los mensajes salientes en Twilio si falla en produccion.

## Canje de premios

PREMIOS / CANJEAR PREMIOS o la opcion 7 muestra hasta 10 premios activos con stock, sus puntos requeridos y el saldo/faltante del cliente. Elegir un premio no descuenta puntos. El asistente muestra nombre, costo, saldo esperado y solicita SI o NO.

Solo SI en el estado de confirmacion del premio llama al servicio de canjes del backend. Se revalida el premio del mismo negocio, saldo, stock y costo actual. Si cambia el costo, se debe consultar y confirmar de nuevo. Se bloquean las filas de saldo y premio para serializar canjes concurrentes entre app y WhatsApp. Descuento de puntos, consumo de lotes, stock, movimiento y codigo se guardan en una transaccion.

Se genera el canje existente con estado GENERATED y codigo GODS-xxxxxx. El cliente presenta el codigo al personal para la entrega; el chat no lo marca USED automaticamente. MIS CANJES u opcion 8 recupera el codigo sin gastar puntos otra vez. NO cancela sin descontar. Un segundo SI no repite el canje; los reintentos del mismo MessageSid se descartan antes de ejecutar acciones.

Se conserva el aislamiento por cliente y negocio verificados, consentimiento, lista de pilotos, suscripcion y cupo mensual. El modelo interpreta solicitudes, pero no elige identidades ni autoriza descuentos. Consultar premios no da acceso a canjes de otras cuentas.

## Despliegue y prueba

Desplegar el backend con los archivos nuevos WhatsappAssistantMenu y WhatsappCustomerActions, ademas de los archivos modificados. No requiere nueva migracion ni nuevas variables para estas opciones: utiliza las tablas de premios, puntos, canjes y reservas existentes. Los premios deben estar configurados y activos en el negocio.

Las opciones aparecen tras enviar MENU al despliegue nuevo. Probar 3, MENU, 5, servicio, dia; luego MENU, 7, premio, NO; repetir y confirmar SI; comprobar movimiento, saldo y stock en la app; enviar MIS CANJES y comprobar el codigo. Probar sin puntos, sin stock y con costo modificado entre consulta y confirmacion.

Las pruebas locales incluyen XML largo con Unicode, promociones vacias/no vacias, opciones de disponibilidad, confirmacion de canje, aislamiento de cuentas, y concurrencia/rollback de canjes con una base H2 y adaptadores de repositorio de prueba. No se envio ningun mensaje real ni se ejecuto un canje de produccion. La entrega real y configuracion de Twilio se verifican despues del despliegue.

Fuentes del transporte: https://www.twilio.com/docs/messaging/twiml y https://help.twilio.com/articles/360033806753-Maximum-Message-Length-with-Twilio-Programmable-Messaging
