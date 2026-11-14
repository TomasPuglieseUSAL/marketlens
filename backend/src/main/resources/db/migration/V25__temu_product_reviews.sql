-- Opiniones del producto, distintas de la reputación de la tienda.
-- NULL = dato no observado; no inferir cero reseñas ni cero ventas.
ALTER TABLE extension_imports ADD COLUMN temu_product_rating NUMERIC(2, 1);
ALTER TABLE extension_imports ADD COLUMN temu_product_review_count INTEGER;

ALTER TABLE publications ADD COLUMN temu_product_rating NUMERIC(2, 1);
ALTER TABLE publications ADD COLUMN temu_product_review_count INTEGER;
ALTER TABLE publications ADD COLUMN temu_product_observed_at TIMESTAMP;

ALTER TABLE extension_imports ADD CONSTRAINT chk_ext_temu_product_rating
    CHECK (temu_product_rating IS NULL OR (temu_product_rating > 0 AND temu_product_rating <= 5));
ALTER TABLE publications ADD CONSTRAINT chk_pub_temu_product_rating
    CHECK (temu_product_rating IS NULL OR (temu_product_rating > 0 AND temu_product_rating <= 5));
ALTER TABLE extension_imports ADD CONSTRAINT chk_ext_temu_product_reviews
    CHECK (temu_product_review_count IS NULL OR temu_product_review_count BETWEEN 0 AND 10000000);
ALTER TABLE publications ADD CONSTRAINT chk_pub_temu_product_reviews
    CHECK (temu_product_review_count IS NULL OR temu_product_review_count BETWEEN 0 AND 10000000);
