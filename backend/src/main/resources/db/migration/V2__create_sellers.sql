CREATE TABLE sellers (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    reputation NUMERIC(4, 3),
    account_age_days INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_seller_reputation CHECK (
        reputation IS NULL OR reputation BETWEEN 0 AND 1
    ),

    CONSTRAINT chk_seller_account_age CHECK (
        account_age_days IS NULL OR account_age_days >= 0
    )
);

ALTER TABLE publications
ADD COLUMN seller_id BIGINT;

INSERT INTO sellers (
    name,
    reputation,
    account_age_days
)
SELECT
    'Imported seller ' || id,
    seller_reputation,
    seller_account_age_days
FROM publications;

UPDATE publications p
SET seller_id = s.id
FROM sellers s
WHERE s.name = 'Imported seller ' || p.id;

ALTER TABLE publications
ALTER COLUMN seller_id SET NOT NULL;

ALTER TABLE publications
ADD CONSTRAINT fk_publication_seller
    FOREIGN KEY (seller_id)
    REFERENCES sellers(id);

CREATE INDEX idx_publications_seller
    ON publications(seller_id);

ALTER TABLE publications
DROP COLUMN seller_reputation;

ALTER TABLE publications
DROP COLUMN seller_account_age_days;