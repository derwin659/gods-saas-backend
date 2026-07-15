-- Prioridad 13: Descubrimiento de negocios afiliados
-- Perfil publico por sede y consentimiento del owner para aparecer en el directorio.

ALTER TABLE branch
    ADD COLUMN IF NOT EXISTS ciudad VARCHAR(120) NULL,
    ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION NULL,
    ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION NULL,
    ADD COLUMN IF NOT EXISTS public_visible BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS directory_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS public_description VARCHAR(500) NULL;

CREATE INDEX IF NOT EXISTS idx_branch_public_directory
    ON branch (public_visible, directory_enabled, activo, ciudad);

CREATE INDEX IF NOT EXISTS idx_branch_public_geo
    ON branch (latitude, longitude);