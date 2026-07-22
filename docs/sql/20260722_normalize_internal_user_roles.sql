-- Corrige asignaciones antiguas que conservaron ADMIN/CASHIER/BARBER
-- después de un cambio de rol. OWNER no se modifica.
UPDATE user_tenant_roles utr
SET role = UPPER(TRIM(u.rol))
FROM app_user u
WHERE u.user_id = utr.user_id
  AND UPPER(TRIM(COALESCE(u.rol, ''))) IN ('ADMIN', 'CASHIER', 'BARBER')
  AND utr.role::text <> UPPER(TRIM(u.rol));