-- Apply after 20261103_whatsapp_embedded_signup.sql, before deploying this version.
ALTER TABLE notification ADD COLUMN IF NOT EXISTS appointment_start timestamp;
ALTER TABLE whatsapp_business_connection ADD COLUMN IF NOT EXISTS sale_receipt_content_sid varchar(255);
ALTER TABLE whatsapp_business_connection ADD COLUMN IF NOT EXISTS reminder60_content_sid varchar(255);
CREATE INDEX IF NOT EXISTS idx_notification_customer_event ON notification(tenant_id,customer_id,type,reference_type,reference_id,appointment_start);
