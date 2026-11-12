ALTER TABLE extension_imports
    ADD COLUMN processed_publication_id BIGINT,
    ADD COLUMN processed_at TIMESTAMPTZ;

ALTER TABLE extension_imports
    ADD CONSTRAINT fk_extension_import_publication
    FOREIGN KEY (processed_publication_id)
    REFERENCES publications(id);

CREATE INDEX idx_extension_imports_processed_publication
    ON extension_imports (processed_publication_id);

CREATE UNIQUE INDEX uq_publications_marketplace_external_id
    ON publications (marketplace, external_id)
    WHERE external_id IS NOT NULL;