-- Auditoria segura de telefonos de clientes antes/despues de habilitar E.164.
--
-- IMPORTANTE:
--   1. Este archivo es SOLO LECTURA: no modifica produccion.
--   2. El backend migra de forma progresiva el telefono legacy a E.164 cuando
--      el cliente solicita su OTP con pais/prefijo.
--   3. No se recomienda anteponer prefijos con un UPDATE masivo: reglas como
--      el 0 troncal de Reino Unido o el 15 movil de Argentina requieren
--      libphonenumber y pueden corromper o fusionar cuentas.

-- A. Tenants cuyo pais falta. Deben corregirse para interpretar numeros locales.
SELECT
    t.tenant_id,
    t.nombre,
    t.pais,
    COUNT(c.customer_id) AS clientes
FROM tenant t
LEFT JOIN customer c ON c.tenant_id = t.tenant_id
WHERE NULLIF(BTRIM(COALESCE(t.pais, '')), '') IS NULL
GROUP BY t.tenant_id, t.nombre, t.pais
ORDER BY t.tenant_id;

-- B. Resumen de telefonos ya internacionales y pendientes de migracion.
SELECT
    t.tenant_id,
    t.nombre AS negocio,
    t.pais,
    COUNT(c.customer_id) AS total_clientes,
    COUNT(*) FILTER (
        WHERE BTRIM(COALESCE(c.telefono, '')) ~ '^\+[1-9][0-9]{7,14}$'
    ) AS telefonos_e164,
    COUNT(*) FILTER (
        WHERE BTRIM(COALESCE(c.telefono, '')) !~ '^\+[1-9][0-9]{7,14}$'
    ) AS pendientes
FROM tenant t
JOIN customer c ON c.tenant_id = t.tenant_id
GROUP BY t.tenant_id, t.nombre, t.pais
ORDER BY pendientes DESC, t.tenant_id;

-- C. Detalle pendiente para revisar casos vacios, extensiones o datos no moviles.
SELECT
    c.customer_id,
    c.tenant_id,
    t.nombre AS negocio,
    t.pais,
    c.telefono,
    REGEXP_REPLACE(COALESCE(c.telefono, ''), '[^0-9]', '', 'g') AS solo_digitos,
    c.activo
FROM customer c
JOIN tenant t ON t.tenant_id = c.tenant_id
WHERE BTRIM(COALESCE(c.telefono, '')) !~ '^\+[1-9][0-9]{7,14}$'
ORDER BY c.tenant_id, c.customer_id;

-- D. Posibles duplicados legacy dentro del mismo tenant.
-- Deben corregirse antes de intentar cualquier migracion masiva.
WITH normalized AS (
    SELECT
        c.customer_id,
        c.tenant_id,
        c.telefono,
        REGEXP_REPLACE(COALESCE(c.telefono, ''), '[^0-9]', '', 'g') AS solo_digitos
    FROM customer c
)
SELECT
    tenant_id,
    solo_digitos,
    COUNT(*) AS coincidencias,
    STRING_AGG(customer_id::text, ', ' ORDER BY customer_id) AS customer_ids,
    STRING_AGG(COALESCE(telefono, '<NULL>'), ' | ' ORDER BY customer_id) AS telefonos
FROM normalized
WHERE solo_digitos <> ''
GROUP BY tenant_id, solo_digitos
HAVING COUNT(*) > 1
ORDER BY tenant_id, solo_digitos;

-- E. Control posterior al despliegue.
-- La cantidad pendiente debe bajar a medida que los clientes ingresan por OTP.
SELECT
    COUNT(*) AS telefonos_pendientes_e164
FROM customer
WHERE BTRIM(COALESCE(telefono, '')) !~ '^\+[1-9][0-9]{7,14}$';
