-- El pipeline de Temu normaliza goods_id como referencia PRODUCT.
-- V8 y V13 se crearon antes de que el backend aceptara Temu y lo rechazan.
-- Conservar el resto de tipos existentes; PRODUCT sólo es válido para TEMU.
-- No modificar migraciones aplicadas: Flyway ejecuta V24 incrementalmente.

ALTER TABLE marketplace_references
    DROP CONSTRAINT chk_marketplace_reference_type;

ALTER TABLE marketplace_references
    ADD CONSTRAINT chk_marketplace_reference_type
    CHECK (
        reference_type IN ('LISTING', 'CATALOG_PRODUCT', 'USER_PRODUCT')
        OR (marketplace = 'TEMU' AND reference_type = 'PRODUCT')
    );

ALTER TABLE publications
    DROP CONSTRAINT chk_publication_reference_type;

ALTER TABLE publications
    ADD CONSTRAINT chk_publication_reference_type
    CHECK (
        reference_type IS NULL
        OR reference_type IN ('LISTING', 'USER_PRODUCT')
        OR (marketplace = 'TEMU' AND reference_type = 'PRODUCT')
    );
