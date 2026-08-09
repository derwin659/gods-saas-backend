-- Verificacion: no modifica datos.
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public' AND table_name = 'professional_showcase'
ORDER BY ordinal_position;

SELECT conname, pg_get_constraintdef(oid)
FROM pg_constraint
WHERE conrelid = 'professional_showcase'::regclass
ORDER BY conname;

SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public' AND tablename = 'professional_showcase'
ORDER BY indexname;

SELECT status, media_type, COUNT(*) AS total
FROM professional_showcase
GROUP BY status, media_type
ORDER BY status, media_type;