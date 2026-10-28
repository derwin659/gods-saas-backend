# Registro y seleccion de barberia por WhatsApp

## Comportamiento

- Numero nuevo: HOLA pregunta el nombre o codigo de la barberia.
- `GODS INDYSTRY`, `INDYSTRY` y `Quiero reservar en INDYSTRY` seleccionan el negocio habilitado.
- Busqueda por nombre parcial: si hay varias coincidencias se muestran hasta 20 opciones. El cliente elige el numero o escribe el codigo exacto. Nunca se escoge una coincidencia ambigua automaticamente.
- Clientes conocidos: HOLA conserva el acceso a su unico negocio elegible; si tienen varios, pide elegir. CAMBIAR BARBERIA/CAMBIAR NEGOCIO siempre permite buscar otro.
- Clientes nuevos: pide nombre, explica el registro y los avisos transaccionales, y requiere ACEPTO o SI ACEPTO. NO cancela sin crear ficha. SALIR cierra la sesion.
- El registro crea una ficha del negocio con telefono E164, origen WHATSAPP, cero puntos y publicidad desactivada. No crea contrasena, activa la app ni concede bonos. La fecha y el texto del consentimiento se guardan en las notas de la ficha.
- La posesion del numero se acredita por el mensaje entrante firmado de Twilio. Si la validacion de firmas esta desactivada, el registro se bloquea. Para cuentas existentes, el canal firmado permite verificar el numero tras consentimiento explicito y las comprobaciones indicadas abajo.
- Si existe una sola ficha activa con ese telefono, sin cambios de telefono pendientes, el asistente permite verificarla y habilitar avisos transaccionales tras responder ACEPTO. No crea otra ficha ni modifica puntos, historial, contrasena, bonos o roles. La publicidad queda desactivada. Un telefono en formato nacional se normaliza solo si corresponde al mismo numero entrante en la region del negocio. Fichas duplicadas, inactivas, numeros que no coinciden y cambios de telefono pendientes siguen requiriendo revision en la app o por el negocio.
- Tras aceptar aparece el menu de reservas/puntos/promociones/servicios/horarios/barberos. Se conserva la confirmacion explicita de reserva y el flujo de adelantos si esta habilitado.
- El alta consume una interaccion del cupo del negocio. Las preguntas de nombre/consentimiento no requieren OpenAI.

## Enlace y QR del negocio

En la pantalla del dueno de configuracion de WhatsApp aparece el codigo, enlace para copiar/compartir y un QR local. El enlace abre el WhatsApp central con `GODS <codigo>` preparado; el cliente debe enviarlo. El QR no envia datos a un generador externo.

La URL proviene de `GET /api/owner/whatsapp-settings/assistant`, con el tenant autenticado. Campos nuevos: `businessCode`, `bookingWhatsappUrl`. Solo se ofrece enlace cuando el asistente esta habilitado para ese negocio y hay un numero remitente valido.

## Despliegue

1. Desplegar el backend con `WhatsappCustomerOnboarding` y los cambios de `WhatsappAssistantService`.
2. Mantener `WHATSAPP_ASSISTANT_ENABLED=true` y el ID real de cada piloto en `WHATSAPP_ASSISTANT_TENANT_IDS`. Para INDYSTRY, agregar su ID real; el codigo no sustituye el ID en esta variable. La suscripcion debe seguir activa y ser elegible.
3. Mantener validacion de firma Twilio habilitada, Auth Token y URL exacta del webhook configurados. No guardar secretos en Git.
4. Esta ampliacion de registro no requiere nuevas tablas ni variables. Para adelantos sigue siendo necesaria la migracion y configuracion de `whatsapp_deposits.md`.
5. Compilar/publicar la app Flutter actualizada para que el dueno vea y comparta el enlace y QR (`qr_flutter`). El chat de registro funciona con el backend actualizado aunque la app aun no se haya publicado.

## Prueba manual antes de ampliar pilotos

Con un numero realmente nuevo: HOLA -> GODS <codigo real> -> nombre -> ACEPTO -> reservar -> elegir servicio/dia/barbero/hora -> revisar resumen -> SI. Si usa adelanto: datos Yape -> captura/referencia -> revisar en agenda -> aprobar -> comprobar aviso.

Repetir con NO, SALIR, codigo inexistente, coincidencias de nombre y una ficha existente sin verificar. Revisar que no se creen fichas duplicadas ni reservas antes de confirmar. Verificar delivery real en Twilio; las pruebas locales no llaman a WhatsApp, OpenAI ni Yape.


## Continuar con una ficha existente

Enviar GODS MALALO (reemplazar MALALO por el codigo real), leer la confirmacion de numero y avisos, y responder ACEPTO. La recepcion de un mensaje con firma Twilio valida acredita control del numero. Se vuelve a validar negocio, identidad de la ficha, telefono, estado y ausencia de duplicados al aceptar, con bloqueo de la ficha. Solo entonces se habilita el menu de cliente. NO cancela sin modificarla.

Una persona cuyo numero tambien se usa como dueno/profesional puede seguir este flujo como cliente. No se modifican AppUser, roles, permisos, contrasenas ni el acceso de dueno. No se permite reactivar fichas inactivas ni resolver colisiones de identidad automaticamente. La verificacion y consentimiento quedan registrados en las notas de la ficha.

La ampliacion requiere desplegar backend; no hay nuevas variables ni migraciones. Despues del despliegue repetir GODS y el codigo para que el asistente inicie la confirmacion nueva.
