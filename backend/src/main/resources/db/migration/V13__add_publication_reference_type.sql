ALTER TABLE publications
    ADD COLUMN reference_type VARCHAR(30);

ALTER TABLE publications
    ADD CONSTRAINT chk_publication_reference_type
    CHECK (
        reference_type IS NULL
        OR reference_type IN (
            'LISTING',
            'USER_PRODUCT'
        )
    );

UPDATE publications
SET reference_type = 'LISTING'
WHERE external_id ~ '^MLA[0-9]+$';

UPDATE publications
SET reference_type = 'USER_PRODUCT'
WHERE external_id ~ '^MLAU[0-9]+$';