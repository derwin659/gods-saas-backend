BEGIN;

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS preferred_locale VARCHAR(10);

ALTER TABLE customer
    ADD COLUMN IF NOT EXISTS preferred_locale VARCHAR(10);

UPDATE tenant_settings ts
SET language = CASE
        WHEN UPPER(REGEXP_REPLACE(COALESCE(t.pais, ''), '[^A-Za-z]', '', 'g')) IN ('BR', 'BRA', 'BRASIL', 'BRAZIL')
            THEN 'pt-BR'
        WHEN UPPER(REGEXP_REPLACE(COALESCE(t.pais, ''), '[^A-Za-z]', '', 'g')) IN ('US', 'USA', 'UNITEDSTATES', 'ESTADOSUNIDOS')
            THEN 'en-US'
        ELSE 'es-PE'
    END,
    timezone = CASE
        WHEN UPPER(REGEXP_REPLACE(COALESCE(t.pais, ''), '[^A-Za-z]', '', 'g')) IN ('BR', 'BRA', 'BRASIL', 'BRAZIL')
            THEN 'America/Sao_Paulo'
        WHEN UPPER(REGEXP_REPLACE(COALESCE(t.pais, ''), '[^A-Za-z]', '', 'g')) IN ('US', 'USA', 'UNITEDSTATES', 'ESTADOSUNIDOS')
            THEN 'America/New_York'
        WHEN UPPER(REGEXP_REPLACE(COALESCE(t.pais, ''), '[^A-Za-z]', '', 'g')) IN ('CO', 'COL', 'COLOMBIA')
            THEN 'America/Bogota'
        WHEN UPPER(REGEXP_REPLACE(COALESCE(t.pais, ''), '[^A-Za-z]', '', 'g')) IN ('VE', 'VEN', 'VENEZUELA')
            THEN 'America/Caracas'
        WHEN UPPER(REGEXP_REPLACE(COALESCE(t.pais, ''), '[^A-Za-z]', '', 'g')) IN ('AR', 'ARG', 'ARGENTINA')
            THEN 'America/Argentina/Buenos_Aires'
        WHEN UPPER(REGEXP_REPLACE(COALESCE(t.pais, ''), '[^A-Za-z]', '', 'g')) IN ('CL', 'CHL', 'CHILE')
            THEN 'America/Santiago'
        WHEN UPPER(REGEXP_REPLACE(COALESCE(t.pais, ''), '[^A-Za-z]', '', 'g')) IN ('EC', 'ECU', 'ECUADOR')
            THEN 'America/Guayaquil'
        WHEN UPPER(REGEXP_REPLACE(COALESCE(t.pais, ''), '[^A-Za-z]', '', 'g')) IN ('MX', 'MEX', 'MEXICO')
            THEN 'America/Mexico_City'
        WHEN UPPER(REGEXP_REPLACE(COALESCE(t.pais, ''), '[^A-Za-z]', '', 'g')) IN ('ES', 'ESP', 'ESPANA', 'SPAIN')
            THEN 'Europe/Madrid'
        WHEN UPPER(REGEXP_REPLACE(COALESCE(t.pais, ''), '[^A-Za-z]', '', 'g')) IN ('PT', 'PRT', 'PORTUGAL')
            THEN 'Europe/Lisbon'
        WHEN UPPER(REGEXP_REPLACE(COALESCE(t.pais, ''), '[^A-Za-z]', '', 'g')) IN ('GB', 'GBR', 'UNITEDKINGDOM', 'REINOUNIDO')
            THEN 'Europe/London'
        ELSE COALESCE(NULLIF(TRIM(ts.timezone), ''), 'America/Lima')
    END,
    updated_at = CURRENT_TIMESTAMP
FROM tenant t
WHERE t.tenant_id = ts.tenant_id
  AND (
      ts.language IS NULL
      OR TRIM(ts.language) = ''
      OR LOWER(TRIM(ts.language)) IN ('es', 'pt', 'en')
      OR ts.timezone IS NULL
      OR TRIM(ts.timezone) = ''
      OR (
          TRIM(ts.timezone) = 'America/Lima'
          AND UPPER(REGEXP_REPLACE(COALESCE(t.pais, ''), '[^A-Za-z]', '', 'g'))
              IN ('BR', 'BRA', 'BRASIL', 'BRAZIL', 'US', 'USA', 'UNITEDSTATES', 'ESTADOSUNIDOS')
      )
  );

UPDATE app_user u
SET preferred_locale = ts.language
FROM tenant_settings ts
WHERE ts.tenant_id = u.tenant_id
  AND (u.preferred_locale IS NULL OR TRIM(u.preferred_locale) = '');

UPDATE customer c
SET preferred_locale = ts.language
FROM tenant_settings ts
WHERE ts.tenant_id = c.tenant_id
  AND (c.preferred_locale IS NULL OR TRIM(c.preferred_locale) = '');

COMMIT;

SELECT
    t.tenant_id,
    t.nombre,
    t.pais,
    ts.language,
    ts.timezone,
    ts.currency
FROM tenant t
JOIN tenant_settings ts ON ts.tenant_id = t.tenant_id
ORDER BY t.tenant_id;
