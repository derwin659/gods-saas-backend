-- Solo lectura: ultimos adelantos y estado de adjuntos WhatsApp.
-- No muestra telefonos, URLs privadas ni credenciales.
SELECT p.appointment_id AS reserva, p.state AS estado_adelanto,
       p.expires_at AS vence, p.reference_supplied AS operacion_recibida,
       m.state AS estado_imagen, m.error_code AS error_imagen,
       m.created_at AS imagen_recibida, m.processing_at AS inicio_procesamiento,
       (m.stored_url IS NOT NULL AND m.stored_url <> '') AS imagen_guardada
FROM whatsapp_assistant_deposit p
LEFT JOIN whatsapp_assistant_deposit_media m ON m.appointment_id = p.appointment_id
ORDER BY p.created_at DESC, m.created_at DESC
LIMIT 20;
