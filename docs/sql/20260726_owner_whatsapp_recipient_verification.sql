BEGIN;

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS whatsapp_verified_phone varchar(32),
    ADD COLUMN IF NOT EXISTS whatsapp_phone_verified_at timestamp without time zone,
    ADD COLUMN IF NOT EXISTS whatsapp_pending_phone varchar(32),
    ADD COLUMN IF NOT EXISTS whatsapp_verification_code_hash varchar(100),
    ADD COLUMN IF NOT EXISTS whatsapp_verification_expires_at timestamp without time zone,
    ADD COLUMN IF NOT EXISTS whatsapp_verification_requested_at timestamp without time zone,
    ADD COLUMN IF NOT EXISTS whatsapp_verification_attempts integer NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_app_user_whatsapp_verified_phone
    ON app_user (whatsapp_verified_phone)
    WHERE whatsapp_phone_verified_at IS NOT NULL;

COMMENT ON COLUMN app_user.whatsapp_verified_phone
    IS 'Numero E.164 que el usuario demostro controlar para recibir alertas operativas.';
COMMENT ON COLUMN app_user.whatsapp_phone_verified_at
    IS 'Fecha de la ultima verificacion exitosa del numero receptor.';
COMMENT ON COLUMN app_user.whatsapp_pending_phone
    IS 'Numero pendiente de confirmar mediante OTP; reemplaza app_user.phone solo al verificar.';
COMMENT ON COLUMN app_user.whatsapp_verification_code_hash
    IS 'Hash BCrypt temporal del OTP. Nunca se almacena el codigo en texto plano.';

COMMIT;
