CREATE TABLE extension_imports (
    id BIGSERIAL PRIMARY KEY,

    marketplace VARCHAR(40) NOT NULL,
    title VARCHAR(500) NOT NULL,

    price_raw VARCHAR(100),
    currency VARCHAR(10),

    seller_name VARCHAR(255),
    external_id VARCHAR(120),

    description TEXT,
    original_url VARCHAR(2048) NOT NULL,

    imported_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_extension_imports_marketplace
    ON extension_imports (marketplace);

CREATE INDEX idx_extension_imports_external_id
    ON extension_imports (marketplace, external_id);

CREATE INDEX idx_extension_imports_imported_by
    ON extension_imports (imported_by);