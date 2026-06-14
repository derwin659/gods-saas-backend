-- Activacion de WhatsApp automatico Twilio para tenant 2 usando el numero central
-- configurado en Railway: TWILIO_WHATSAPP_FROM_NUMBER.
-- No se configura whatsappSenderPhone para que NO use el numero del dueno/admin.

UPDATE tenant_settings
SET schedule_config = COALESCE(schedule_config, '{}'::jsonb)
    || jsonb_build_object(
        'whatsappProvider', 'TWILIO',
        'whatsappConnectionStatus', 'CONNECTED',
        'whatsappPostSaleMessageEnabled', true,
        'whatsappReminder60Enabled', true,
        'whatsappReminder24hEnabled', false,
        'whatsappSenderLabel', 'Super Gods'
    ),
    updated_at = NOW()
WHERE tenant_id = 2;
