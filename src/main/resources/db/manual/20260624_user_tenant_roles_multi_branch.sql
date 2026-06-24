-- Permite asignar un mismo usuario/barbero a varias sedes dentro del mismo tenant.
-- Antes existia una restriccion unica por (user_id, tenant_id, role), que bloqueaba
-- multiples filas BARBER para distintas branch_id.

ALTER TABLE user_tenant_roles
    DROP CONSTRAINT IF EXISTS uk_user_tenant_roles_unique;

DROP INDEX IF EXISTS uk_user_tenant_roles_unique;

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_tenant_roles_branch_unique
    ON user_tenant_roles (user_id, tenant_id, role, branch_id)
    WHERE branch_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_tenant_roles_no_branch_unique
    ON user_tenant_roles (user_id, tenant_id, role)
    WHERE branch_id IS NULL;
