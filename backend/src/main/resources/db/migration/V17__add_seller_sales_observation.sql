-- Si ya aplicaste la migración anterior, NO es necesario repetirla.
-- Ejecutar manualmente en pgAdmin o renombrar con la próxima versión Flyway.
ALTER TABLE sellers
    ADD COLUMN IF NOT EXISTS completed_sales INTEGER,
    ADD COLUMN IF NOT EXISTS sales_observed_at TIMESTAMP;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'chk_sellers_completed_sales_nonnegative'
          AND conrelid = 'sellers'::regclass
    ) THEN
        ALTER TABLE sellers
            ADD CONSTRAINT chk_sellers_completed_sales_nonnegative
            CHECK (completed_sales IS NULL OR completed_sales >= 0);
    END IF;
END $$;
