ALTER TABLE extension_imports
    ADD COLUMN facebook_join_year INTEGER;

ALTER TABLE extension_imports
    ADD CONSTRAINT chk_extension_import_facebook_join_year
    CHECK (
        facebook_join_year IS NULL
        OR (
            marketplace = 'FACEBOOK_MARKETPLACE'
            AND facebook_join_year BETWEEN 2004 AND 2100
        )
    );