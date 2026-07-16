-- Prioridad 13: metricas agregadas del directorio, sin ubicacion del cliente.
CREATE TABLE IF NOT EXISTS affiliated_discovery_event (
    affiliated_discovery_event_id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL REFERENCES tenant(tenant_id),
    branch_id BIGINT NOT NULL REFERENCES branch(branch_id),
    event_type VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_affiliated_event_tenant_branch_type
    ON affiliated_discovery_event (tenant_id, branch_id, event_type);
CREATE INDEX IF NOT EXISTS idx_affiliated_event_created_at
    ON affiliated_discovery_event (created_at);