ALTER TABLE extension_imports
    ADD COLUMN seller_reputation_level SMALLINT;

ALTER TABLE extension_imports
    ADD CONSTRAINT chk_extension_seller_reputation_level
    CHECK (
        seller_reputation_level IS NULL
        OR seller_reputation_level BETWEEN 1 AND 5
    );