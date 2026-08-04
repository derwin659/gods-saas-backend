ALTER TABLE subscription
    ADD COLUMN IF NOT EXISTS unlimited_rewards_enabled BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN subscription.unlimited_rewards_enabled IS
    'Excepción comercial: permite premios personalizados ilimitados sin cambiar el plan.';