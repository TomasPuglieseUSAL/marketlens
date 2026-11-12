ALTER TABLE sellers
    ADD COLUMN marketplace VARCHAR(30),
    ADD COLUMN external_seller_id BIGINT,
    ADD COLUMN reputation_level INTEGER;

ALTER TABLE sellers
    ADD CONSTRAINT chk_seller_reputation_level
    CHECK (
        reputation_level IS NULL
        OR reputation_level BETWEEN 1 AND 5
    );

ALTER TABLE sellers
    ADD CONSTRAINT chk_seller_external_identity
    CHECK (
        (marketplace IS NULL AND external_seller_id IS NULL)
        OR
        (marketplace IS NOT NULL AND external_seller_id > 0)
    );

CREATE UNIQUE INDEX uq_seller_marketplace_external_id
    ON sellers (marketplace, external_seller_id)
    WHERE external_seller_id IS NOT NULL;