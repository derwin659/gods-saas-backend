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
('STARTER','CO','COP','MONTHLY',49000),('PRO','CO','COP','MONTHLY',99000),('GODS_AI','CO','COP','MONTHLY',189000),
('STARTER','MX','MXN','MONTHLY',249),('PRO','MX','MXN','MONTHLY',499),('GODS_AI','MX','MXN','MONTHLY',949),
('STARTER','CL','CLP','MONTHLY',11900),('PRO','CL','CLP','MONTHLY',24900),('GODS_AI','CL','CLP','MONTHLY',44900),
('STARTER','AR','ARS','MONTHLY',12000),('PRO','AR','ARS','MONTHLY',25000),('GODS_AI','AR','ARS','MONTHLY',49000),
('STARTER','BO','BOB','MONTHLY',59),('PRO','BO','BOB','MONTHLY',119),('GODS_AI','BO','BOB','MONTHLY',229),
('STARTER','BR','BRL','MONTHLY',59),('PRO','BR','BRL','MONTHLY',119),('GODS_AI','BR','BRL','MONTHLY',229),
('STARTER','EU','EUR','MONTHLY',17),('PRO','EU','EUR','MONTHLY',35),('GODS_AI','EU','EUR','MONTHLY',69),
('STARTER','UY','UYU','MONTHLY',590),('PRO','UY','UYU','MONTHLY',1190),('GODS_AI','UY','UYU','MONTHLY',2290),
('STARTER','PY','PYG','MONTHLY',79000),('PRO','PY','PYG','MONTHLY',159000),('GODS_AI','PY','PYG','MONTHLY',299000),
('STARTER','CR','CRC','MONTHLY',7900),('PRO','CR','CRC','MONTHLY',15900),('GODS_AI','CR','CRC','MONTHLY',29900),
('STARTER','DO','DOP','MONTHLY',699),('PRO','DO','DOP','MONTHLY',1399),('GODS_AI','DO','DOP','MONTHLY',2699),
('STARTER','GT','GTQ','MONTHLY',59),('PRO','GT','GTQ','MONTHLY',119),('GODS_AI','GT','GTQ','MONTHLY',229)
ON CONFLICT (plan_code, country_code, billing_cycle) DO UPDATE SET
    currency = EXCLUDED.currency,
    amount = EXCLUDED.amount,
    active = TRUE,
    updated_at = NOW();


    price id starter 

    pri_01ksge6ezebjjhq4gt887fnqt1

    price id PRO

    pri_01ksgeahph8s0j59vm4ba2g1t2

    price_id GODS GODS_AI

    pri_01ksgdxcatyps3scp8eqpnhv7n
