-- Apply before deploying the guided WhatsApp onboarding feature. Safe to repeat.
CREATE TABLE IF NOT EXISTS whatsapp_business_connection (
 tenant_id bigint PRIMARY KEY REFERENCES tenant(tenant_id),
 phone varchar(20) NOT NULL UNIQUE,
 account_sid varchar(34) UNIQUE,
 encrypted_auth_token varchar(512),
 waba_id varchar(40) UNIQUE,
 sender_sid varchar(34) UNIQUE,
 state varchar(32) NOT NULL DEFAULT 'RESERVED',
 error_code varchar(64),
 booking_content_sid varchar(255),
 deposit_content_sid varchar(255),
 updated_at timestamp with time zone,
 version bigint
);
CREATE TABLE IF NOT EXISTS whatsapp_onboarding_session (
 id varchar(255) PRIMARY KEY,
 tenant_id bigint NOT NULL REFERENCES tenant(tenant_id),
 owner_id bigint NOT NULL,
 phone varchar(20) NOT NULL,
 ticket_hash varchar(64) NOT NULL UNIQUE,
 access_hash varchar(64) UNIQUE,
 ticket_expires_at timestamp with time zone NOT NULL,
 expires_at timestamp with time zone NOT NULL,
 version bigint
);
CREATE INDEX IF NOT EXISTS whatsapp_onboarding_expiry ON whatsapp_onboarding_session(expires_at);
-- Tokens and tickets are stored only as SHA-256 hashes. Subaccount auth tokens use tenant-bound AES-256-GCM.
-- Expired sessions can be removed by operations; do not delete connection rows automatically.
