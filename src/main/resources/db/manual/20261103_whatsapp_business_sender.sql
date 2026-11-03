-- Apply before deploying the business sender feature. Safe to repeat.
ALTER TABLE IF EXISTS whatsapp_assistant_session ADD COLUMN IF NOT EXISTS destination_phone varchar(20);
ALTER TABLE IF EXISTS whatsapp_assistant_deposit ADD COLUMN IF NOT EXISTS sender_phone varchar(20);
