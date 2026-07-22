-- Corrige solo asignaciones antiguas duplicadas.
-- Nunca modifica ni elimina roles OWNER: un dueño puede ser también BARBER.
BEGIN;

DELETE FROM user_tenant_roles obsolete
USING user_tenant_roles desired_utr
WHERE desired_utr.user_id = obsolete.user_id
  AND desired_utr.tenant_id = obsolete.tenant_id
  AND desired_utr.branch_id IS NOT DISTINCT FROM obsolete.branch_id
  AND desired_utr.role = 'BARBER'
  AND obsolete.role IN ('ADMIN', 'CASHIER');

COMMIT;