-- Opiniones de la PUBLICACIÓN verificadas por reviewable_object (type=item).
-- No reutilizar las métricas ml_product_*: son agregados de catálogo.
ALTER TABLE publications
    ADD COLUMN ml_item_review_rating NUMERIC(2,1),
    ADD COLUMN ml_item_review_count INTEGER,
    ADD COLUMN ml_item_review_verified_id VARCHAR(120),
    ADD COLUMN ml_item_review_observed_at TIMESTAMP;

ALTER TABLE publications
    ADD CONSTRAINT ck_ml_item_review_count_valid
    CHECK (ml_item_review_count IS NULL OR ml_item_review_count BETWEEN 1 AND 10000000),
    ADD CONSTRAINT ck_ml_item_review_rating_valid
    CHECK (ml_item_review_rating IS NULL OR ml_item_review_rating > 0 AND ml_item_review_rating <= 5);
