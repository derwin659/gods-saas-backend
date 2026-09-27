# Vencimiento de puntos por ventas

Desplegar primero el backend, luego la web y la app móvil. No requiere cambio de esquema: usa scheduleConfig del negocio.

En Fidelización, el dueño puede activar pointsExpirationEnabled y definir pointsExpirationDays (1–3650). Por defecto queda desactivado, incluso para negocios existentes sin configuración explícita. El trabajo diario respeta esta opción. Los clientes móviles anteriores que no envían los nuevos campos conservan la configuración guardada.

Al activar o modificar el plazo se reprograman los lotes ACTIVE del negocio desde el momento del cambio. Guardar otras opciones no renueva el plazo. Las ventas nuevas reciben su plazo desde la venta. Desactivar suspende los vencimientos; no restaura saldos anteriores. Solo vencen puntos por ventas: bonos y ajustes positivos no tienen lotes. Los puntos acumulados de categoría no se reducen.

Los canjes nuevos y ajustes negativos consumen primero los lotes que vencen antes. Esto evita que esos puntos se descuenten otra vez. No se reconstruyen automáticamente los lotes de canjes anteriores a esta corrección. Antes de habilitar vencimiento en negocios con canjes históricos, revisar esos lotes y el historial; mantener sin vencimiento mientras se concilian. Tampoco se devuelven descuentos históricos sin revisar su motivo.

Para investigar un reclamo, consultar loyalty_movement filtrando tenant_id y customer_id; revisar tipo, origen, puntos, saldo_resultante y fecha_creacion. EXPIRE / EXPIRATION indica vencimiento; REDEEM indica canje; ADJUST indica ajuste. No modificar saldos directamente: cualquier devolución comprobada debe registrarse con motivo mediante el ajuste de puntos existente.

Validación: LoyaltyExpirationTest (7 pruebas), compilación web y análisis del formulario móvil.
