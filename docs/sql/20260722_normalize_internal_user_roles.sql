BEGIN;

-- Si un usuario ya posee el rol actual para la misma sede, elimina primero
-- la asignación antigua (por ejemplo ADMIN) que chocaría al convertirla.
DELETE FROM user_tenant_roles obsolete
USING app_user u, user_tenant_roles desired_utr
WHERE u.user_id = obsolete.user_id
  AND desired_utr.user_id = obsolete.user_id
  AND desired_utr.tenant_id = obsolete.tenant_id
  AND desired_utr.branch_id IS NOT DISTINCT FROM obsolete.branch_id
  AND UPPER(TRIM(COALESCE(u.rol, ''))) IN ('ADMIN', 'CASHIER', 'BARBER')
  AND desired_utr.role::text = UPPER(TRIM(u.rol))
  AND obsolete.role::text <> UPPER(TRIM(u.rol));

-- Normaliza los registros restantes al rol principal actual del usuario.
UPDATE user_tenant_roles utr
SET role = UPPER(TRIM(u.rol))
FROM app_user u
WHERE u.user_id = utr.user_id
  AND UPPER(TRIM(COALESCE(u.rol, ''))) IN ('ADMIN', 'CASHIER', 'BARBER')
  AND utr.role::text <> UPPER(TRIM(u.rol));

COMMIT;