ALTER TABLE extension_imports
    ADD COLUMN external_seller_id BIGINT;

ALTER TABLE extension_imports
    ADD CONSTRAINT chk_extension_seller_id
    CHECK (
        external_seller_id IS NULL
        OR external_seller_id > 0
    );