# Invitaciones a reseñas de Google por sede

## Configuracion

Aplicar src/main/resources/db/manual/20261104_google_review_links.sql antes de desplegar el backend. Agrega google_review_url y google_review_whatsapp_enabled a branch; la invitacion WhatsApp queda apagada por defecto. Publicar backend, web y actualizacion movil.

En web o movil: Configuracion > Sedes > editar sede > Reseñas en Google. Copiar desde el Perfil de Empresa de Google el enlace oficial para solicitar reseñas y pegarlo en la sede correspondiente. Guardar y comprobar que abre la ficha correcta. No requiere OAuth ni claves de Google.

Se admiten enlaces HTTPS de g.page/.../review, search.google.com/local/writereview?placeid=..., maps.app.goo.gl y google.com/maps. No se siguen redirecciones en el servidor ni se aceptan enlaces de otros dominios o con credenciales embebidas.

Los clientes ven Opinar en Google en sus visitas del historial y en Mis reseñas, con independencia de haber calificado ya o de la cantidad de estrellas. El cliente decide si publica con su cuenta de Google; SuperGods no publica, traslada textos, preselecciona estrellas ni confirma una publicacion.

La web publica /opinar/{branchId} muestra una invitacion neutral y el boton a Google. Se consulta /api/public/google-reviews/{branchId}; responde solo nombre comercial, nombre de sede y enlace validado. No exige que la sede aparezca en el directorio, pero la sede y negocio deben estar activos y tener el enlace configurado. El historial usa solamente ventas aprobadas del cliente y une sedes por branch_id y tenant_id.

Las reseñas internas siguen separadas. No se dan puntos, premios o descuentos por reseñar en Google, ni se filtra la invitacion por la nota de SuperGods. Los puntos mostrados en postventa corresponden a la compra.

## WhatsApp opcional

El enlace en la app no cambia las plantillas existentes. Dejar apagada Incluir invitacion en el WhatsApp postventa hasta preparar la plantilla correspondiente si se utiliza envio automatico.

Al activarlo, el mensaje manual y automatico agregan este bloque antes de Te esperamos pronto:

Si deseas compartir tu experiencia, puedes dejar una resena en Google:
{enlace de la sede}
Tu opinion es voluntaria.

Las nuevas variantes terminan en _google. Sus cuerpos, ejemplos y nombres v3 estan en docs/whatsapp-postventa-templates/*_google.txt y *_google.json. Para todas las opciones activadas: points_app_code_booking_assistant_google.txt. El enlace Google es la variable 11 en esa version; si se omiten bloques cambia su numero, por eso debe utilizarse el JSON de cada variante.

Crear y aprobar las variantes utilizadas en la subcuenta del negocio como Marketing. Guardar cada SID HX bajo la clave exacta de la variante en whatsapp_business_connection.sale_receipt_content_sids. Ejemplo: {"points_app_code_booking_assistant_google":"HX..."}. En la provision manual segura TWILIO_WHATSAPP_BUSINESS_CONNECTIONS se utiliza saleReceiptContentSids. No usar el SID anterior de la plantilla sin Google: tiene otro contenido y otro numero de variables. Si falta el SID correcto, el envio falla explicitamente en vez de enviar un contenido diferente.

El opt-in de la sede no altera los consentimientos del cliente: siguen siendo necesarios consentimiento transaccional y de marketing cuando corresponda, y se respeta la baja. La copia guardada al aprobar la venta conserva el enlace y los puntos de esa venta. Apagar la opcion afecta a las ventas nuevas; no cambia mensajes historicos ya preparados.

## Verificacion

- Guardar un enlace para una sede y comprobar el destino con una cuenta cliente.
- Ver el boton tanto sin calificacion como despues de una reseña de 1 o 5 estrellas.
- Sede sin enlace o desactivada: no mostrar un enlace disponible.
- Enlaces de dominios ajenos o destinos no admitidos: rechazar al guardar.
- Sin habilitar WhatsApp: no cambia el cuerpo ni la variante de los mensajes existentes.
- Habilitado y plantilla/consentimientos listos: comprobar el enlace de esa sede en el mensaje, conservando puntos y enlaces anteriores.

No se publico ningun cambio ni se enviaron mensajes reales durante el desarrollo.
