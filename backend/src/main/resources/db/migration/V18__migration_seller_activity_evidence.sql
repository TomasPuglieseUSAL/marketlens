-- Ejecutar UNA SOLA VEZ mediante pgAdmin, O por Flyway; nunca ambas rutas.
-- Los datos ausentes siguen siendo NULL: no se presumen ventas cero.
ALTER TABLE sellers
    ADD COLUMN IF NOT EXISTS listed_products_count INTEGER,
    ADD COLUMN IF NOT EXISTS products_observed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS insufficient_sales_for_reputation BOOLEAN,
    ADD COLUMN IF NOT EXISTS insufficient_sales_observed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS sales_count_not_displayed_observed_at TIMESTAMP;
