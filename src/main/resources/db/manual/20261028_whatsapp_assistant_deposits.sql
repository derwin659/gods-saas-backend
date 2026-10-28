-- Apply before enabling WHATSAPP_ASSISTANT_DEPOSITS_ENABLED.
CREATE TABLE IF NOT EXISTS whatsapp_assistant_deposit (
 appointment_id bigint PRIMARY KEY REFERENCES appointment(appointment_id),
 tenant_id bigint NOT NULL REFERENCES tenant(tenant_id),
 customer_id bigint NOT NULL REFERENCES customer(customer_id),
 phone varchar(20) NOT NULL,
 state varchar(32) NOT NULL,
 expires_at timestamp with time zone NOT NULL,
 last_inbound_at timestamp with time zone NOT NULL,
 requires_reference boolean NOT NULL DEFAULT true,
 reference_supplied boolean NOT NULL DEFAULT false,
 instructions text NOT NULL,
 revision integer NOT NULL DEFAULT 0,
 created_at timestamp with time zone NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS whatsapp_assistant_deposit_phone ON whatsapp_assistant_deposit(phone,state);
CREATE TABLE IF NOT EXISTS whatsapp_assistant_deposit_media (
 message_sid varchar(64) PRIMARY KEY,
 appointment_id bigint NOT NULL REFERENCES whatsapp_assistant_deposit(appointment_id),
 media_url text NOT NULL,
 content_type varchar(60) NOT NULL,
 stored_url text,
 operation_reference varchar(120),
 state varchar(24) NOT NULL DEFAULT 'PENDING',
 processing_at timestamp with time zone,
 created_at timestamp with time zone NOT NULL DEFAULT now(),
 error_code varchar(100)
);
CREATE TABLE IF NOT EXISTS whatsapp_assistant_deposit_notice (
 event_key varchar(120) PRIMARY KEY,
 appointment_id bigint NOT NULL REFERENCES whatsapp_assistant_deposit(appointment_id),
 body text NOT NULL,
 state varchar(24) NOT NULL DEFAULT 'PENDING',
 processing_at timestamp with time zone,
 provider_sid varchar(64),
 created_at timestamp with time zone NOT NULL DEFAULT now(),
 error_code varchar(100)
);
-- PROCESSING notices interrupted by a restart require manual reconciliation with Twilio.
-- Never blindly resend an ambiguous send: the provider may have accepted it.
