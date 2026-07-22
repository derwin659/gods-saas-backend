-- Origen de fondos para gastos, adelantos y pagos de profesionales.
-- Los registros existentes conservan el comportamiento anterior: caja actual.
ALTER TABLE cash_movement
    ADD COLUMN IF NOT EXISTS funding_source VARCHAR(30) NOT NULL DEFAULT 'CASH_REGISTER',
    ADD COLUMN IF NOT EXISTS cash_fund_movement_id BIGINT NULL REFERENCES cash_fund_movement(cash_fund_movement_id);

CREATE INDEX IF NOT EXISTS idx_cash_movement_fund_movement
    ON cash_movement (cash_fund_movement_id);

-- El cierre automatico es provisional: se concilia cuando el dueno vuelve a abrir caja.
ALTER TABLE cash_register
    ADD COLUMN IF NOT EXISTS reconciliation_required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS reconciliation_note VARCHAR(500);