CREATE TABLE IF NOT EXISTS subscription_plan_price (
    id BIGSERIAL PRIMARY KEY,
    plan_code VARCHAR(40) NOT NULL,
    country_code VARCHAR(8) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    billing_cycle VARCHAR(20) NOT NULL DEFAULT 'MONTHLY',
    amount NUMERIC(12,2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_subscription_plan_price UNIQUE (plan_code, country_code, billing_cycle)
);

INSERT INTO subscription_plan_price (plan_code, country_code, currency, billing_cycle, amount) VALUES
('STARTER','PE','PEN','MONTHLY',39),('PRO','PE','PEN','MONTHLY',79),('GODS_AI','PE','PEN','MONTHLY',149),
('STARTER','US','USD','MONTHLY',19),('PRO','US','USD','MONTHLY',39),('GODS_AI','US','USD','MONTHLY',79),
('STARTER','CO','COP','MONTHLY',79000),('PRO','CO','COP','MONTHLY',159000),('GODS_AI','CO','COP','MONTHLY',319000),
('STARTER','MX','MXN','MONTHLY',369),('PRO','MX','MXN','MONTHLY',759),('GODS_AI','MX','MXN','MONTHLY',1529),
('STARTER','CL','CLP','MONTHLY',18900),('PRO','CL','CLP','MONTHLY',38900),('GODS_AI','CL','CLP','MONTHLY',78900),
('STARTER','AR','ARS','MONTHLY',22000),('PRO','AR','ARS','MONTHLY',44900),('GODS_AI','AR','ARS','MONTHLY',89900),
('STARTER','BO','USD','MONTHLY',19),('PRO','BO','USD','MONTHLY',39),('GODS_AI','BO','USD','MONTHLY',79),
('STARTER','BR','BRL','MONTHLY',99),('PRO','BR','BRL','MONTHLY',199),('GODS_AI','BR','BRL','MONTHLY',399),
('STARTER','EU','EUR','MONTHLY',17),('PRO','EU','EUR','MONTHLY',35),('GODS_AI','EU','EUR','MONTHLY',69),
('STARTER','UY','USD','MONTHLY',19),('PRO','UY','USD','MONTHLY',39),('GODS_AI','UY','USD','MONTHLY',79),
('STARTER','PY','USD','MONTHLY',19),('PRO','PY','USD','MONTHLY',39),('GODS_AI','PY','USD','MONTHLY',79),
('STARTER','CR','USD','MONTHLY',19),('PRO','CR','USD','MONTHLY',39),('GODS_AI','CR','USD','MONTHLY',79),
('STARTER','DO','USD','MONTHLY',19),('PRO','DO','USD','MONTHLY',39),('GODS_AI','DO','USD','MONTHLY',79),
('STARTER','GT','USD','MONTHLY',19),('PRO','GT','USD','MONTHLY',39),('GODS_AI','GT','USD','MONTHLY',79)
ON CONFLICT (plan_code, country_code, billing_cycle) DO UPDATE SET
    currency = EXCLUDED.currency,
    amount = EXCLUDED.amount,
    active = TRUE,
    updated_at = NOW();
