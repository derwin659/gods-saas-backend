-- Apply before deploying SERVICE support if the schema has a redirect_type CHECK.
-- Only replace checks that reference this column alone; retain all other checks.
BEGIN;
DO $$
DECLARE item record;
DECLARE column_number smallint;
BEGIN
  SELECT attnum INTO column_number FROM pg_attribute
   WHERE attrelid = 'promotion'::regclass AND attname = 'redirect_type';
  FOR item IN SELECT conname FROM pg_constraint
    WHERE conrelid = 'promotion'::regclass AND contype = 'c'
      AND conkey = ARRAY[column_number]::smallint[]
  LOOP
    EXECUTE format('ALTER TABLE promotion DROP CONSTRAINT %I', item.conname);
  END LOOP;
END $$;
ALTER TABLE promotion ADD CONSTRAINT promotion_redirect_type_check
  CHECK (redirect_type IN ('NONE','BOOKING','SERVICE','POINTS','REWARD','PROMO_DETAIL'));
COMMIT;
