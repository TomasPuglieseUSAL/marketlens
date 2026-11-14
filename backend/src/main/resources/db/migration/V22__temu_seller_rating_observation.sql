-- Puntuación de tienda Temu observada junto al nombre (0.1 a 5.0).
-- Es distinta de aggregateRating del producto y reputación de Mercado Libre.
ALTER TABLE extension_imports ADD COLUMN temu_seller_rating NUMERIC(2,1);
ALTER TABLE publications ADD COLUMN temu_seller_rating NUMERIC(2,1);

ALTER TABLE extension_imports ADD CONSTRAINT chk_extension_temu_rating
    CHECK (temu_seller_rating IS NULL OR (temu_seller_rating > 0 AND temu_seller_rating <= 5));
ALTER TABLE publications ADD CONSTRAINT chk_publication_temu_rating
    CHECK (temu_seller_rating IS NULL OR (temu_seller_rating > 0 AND temu_seller_rating <= 5));
