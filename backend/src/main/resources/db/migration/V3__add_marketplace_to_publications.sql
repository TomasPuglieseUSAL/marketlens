ALTER TABLE publications
ADD COLUMN marketplace VARCHAR(50);

ALTER TABLE publications
ADD COLUMN external_id VARCHAR(255);

ALTER TABLE publications
ADD COLUMN original_url VARCHAR(1000);

UPDATE publications
SET marketplace = 'OTHER'
WHERE marketplace IS NULL;

ALTER TABLE publications
ALTER COLUMN marketplace SET NOT NULL;

ALTER TABLE publications
ADD CONSTRAINT chk_publication_marketplace
CHECK (
    marketplace IN (
        'MERCADO_LIBRE',
        'FACEBOOK_MARKETPLACE',
        'TEMU',
        'OTHER'
    )
);

CREATE INDEX idx_publications_marketplace
    ON publications(marketplace);

CREATE INDEX idx_publications_external_id
    ON publications(external_id);