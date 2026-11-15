-- Indicadores comerciales adicionales de Temu. Mantener el texto observado
-- para preservar distinción entre cantidades aproximadas y exactas.
-- Antigüedad de TIENDA distinta del historial de ventas del PRODUCTO.
ALTER TABLE extension_imports
    ADD COLUMN temu_store_tenure_text VARCHAR(60),
    ADD COLUMN temu_product_sold_text VARCHAR(60);

ALTER TABLE publications
    ADD COLUMN temu_store_tenure_text VARCHAR(60),
    ADD COLUMN temu_store_tenure_observed_at TIMESTAMP,
    ADD COLUMN temu_product_sold_text VARCHAR(60),
    ADD COLUMN temu_product_sales_observed_at TIMESTAMP;
