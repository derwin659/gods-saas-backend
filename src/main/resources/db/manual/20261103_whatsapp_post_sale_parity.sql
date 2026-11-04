-- Apply after 20261103_whatsapp_sale_reminders.sql. Safe to repeat.
ALTER TABLE sale ADD COLUMN IF NOT EXISTS whatsapp_receipt_snapshot jsonb;
ALTER TABLE notification ADD COLUMN IF NOT EXISTS whatsapp_template_snapshot jsonb;
ALTER TABLE whatsapp_business_connection ADD COLUMN IF NOT EXISTS sale_receipt_content_sids jsonb;
