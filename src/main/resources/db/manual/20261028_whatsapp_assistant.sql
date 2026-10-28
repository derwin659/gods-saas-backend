-- Pilot is disabled by default. Apply manually before enabling whatsapp.assistant.enabled.
CREATE TABLE IF NOT EXISTS whatsapp_assistant_session (
    phone varchar(20) PRIMARY KEY,
    tenant_id bigint REFERENCES tenant(tenant_id),
    draft text,
    confirmation_code varchar(12),
    quote numeric(12,2),
    expires_at timestamp with time zone,
    last_turn_at timestamp with time zone
);
CREATE TABLE IF NOT EXISTS whatsapp_assistant_message (
    message_sid varchar(64) PRIMARY KEY,
    created_at timestamp with time zone NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS whatsapp_assistant_usage (
    tenant_id bigint NOT NULL REFERENCES tenant(tenant_id),
    month_key varchar(7) NOT NULL,
    turns integer NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, month_key)
);
-- Retain MessageSid rows for the provider retry window (at least 7 days).
-- Expired sessions may be cleared by operations without altering appointments.
