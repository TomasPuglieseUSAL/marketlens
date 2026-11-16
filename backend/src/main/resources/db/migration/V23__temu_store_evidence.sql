-- Identidad de tienda y observaciones por PUBLICACIÓN, no verificación de identidad.
ALTER TABLE extension_imports ADD COLUMN temu_seller_review_count INTEGER;
ALTER TABLE extension_imports ADD COLUMN temu_store_id VARCHAR(30);
ALTER TABLE extension_imports ADD COLUMN temu_seller_section_observed BOOLEAN;
ALTER TABLE publications ADD COLUMN temu_seller_review_count INTEGER;
ALTER TABLE publications ADD COLUMN temu_store_id VARCHAR(30);
ALTER TABLE publications ADD COLUMN temu_seller_section_observed BOOLEAN;
ALTER TABLE publications ADD COLUMN temu_seller_observed_at TIMESTAMP;

ALTER TABLE extension_imports ADD CONSTRAINT chk_ext_temu_reviews
    CHECK (temu_seller_review_count IS NULL OR temu_seller_review_count BETWEEN 0 AND 10000000);
ALTER TABLE publications ADD CONSTRAINT chk_pub_temu_reviews
    CHECK (temu_seller_review_count IS NULL OR temu_seller_review_count BETWEEN 0 AND 10000000);
