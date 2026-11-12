ALTER TABLE extension_imports
    ADD COLUMN seller_profile_url VARCHAR(2048),
    ADD COLUMN seller_badge VARCHAR(80),
    ADD COLUMN seller_sales_text VARCHAR(120);