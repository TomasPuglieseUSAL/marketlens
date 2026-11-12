CREATE TABLE marketplace_references (
    id BIGSERIAL PRIMARY KEY,

    marketplace VARCHAR(40) NOT NULL,
    reference_type VARCHAR(40) NOT NULL,
    external_id VARCHAR(120) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_marketplace_reference
        UNIQUE (marketplace, reference_type, external_id),

    CONSTRAINT chk_marketplace_reference_type
        CHECK (reference_type IN (
            'LISTING',
            'CATALOG_PRODUCT',
            'USER_PRODUCT'
        ))
);

ALTER TABLE extension_imports
    ADD COLUMN marketplace_reference_id BIGINT;

ALTER TABLE extension_imports
    ADD CONSTRAINT fk_extension_import_reference
    FOREIGN KEY (marketplace_reference_id)
    REFERENCES marketplace_references(id);

CREATE INDEX idx_extension_imports_reference
    ON extension_imports (marketplace_reference_id);