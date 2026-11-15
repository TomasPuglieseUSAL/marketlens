-- Señales públicas de Mercado Libre. No convertir observaciones de la página
-- en reputación verificada ni confundir opiniones del producto con el vendedor.
ALTER TABLE extension_imports
    ADD COLUMN ml_product_rating NUMERIC(2,1),
    ADD COLUMN ml_product_review_count INTEGER;

ALTER TABLE publications
    ADD COLUMN ml_product_rating NUMERIC(2,1),
    ADD COLUMN ml_product_review_count INTEGER,
    ADD COLUMN ml_product_observed_at TIMESTAMP,
    ADD COLUMN ml_seller_reputation_observed INTEGER,
    ADD COLUMN ml_seller_reputation_observed_at TIMESTAMP;
