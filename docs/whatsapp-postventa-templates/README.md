# Plantillas postventa completas, version 2

Cada .txt contiene el cuerpo exacto para Twilio. El .json del mismo nombre contiene nombre sugerido, idioma, categoria, ejemplos por variable y clave de configuracion.

Crea solo las variantes que utiliza el negocio, en su propia subcuenta. Text = cuerpo del .txt, Spanish = idioma, MARKETING para mensajes con invitaciones a descargar o reservar. La variante points, sin invitaciones, puede solicitarse como UTILITY; Meta decide la categoria/aprobacion final.

- points: solo saludo y puntos.
- _app: incluye descarga de app.
- _code: instrucciones con codigo de negocio, si hay app y codigo.
- _booking: incluye reserva web.
- _assistant: incluye enlace e instrucciones del asistente.

Ejemplo completo: points_app_code_booking_assistant.txt. Guardar su SID HX en sale_receipt_content_sids bajo esa misma clave. La plantilla de solo puntos y la de enlaces deshabilitados tienen otros cuerpos y otros SID. No colocar el mismo SID bajo claves con cuerpos distintos.

El nombre de la plantilla anterior supergods_venta_aprobada_v1 no representa este contrato; usar los nombres version 2. No se han creado ni enviado a aprobacion estas plantillas en Twilio: estos son archivos locales para crearlas.
