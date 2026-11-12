CREATE TABLE publications (
    id BIGSERIAL PRIMARY KEY,

    title VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(15, 2) NOT NULL,

    seller_reputation NUMERIC(4, 3),
    seller_account_age_days INTEGER,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_publication_price
        CHECK (price >= 0),

    CONSTRAINT chk_seller_reputation
        CHECK (
            seller_reputation IS NULL
            OR seller_reputation BETWEEN 0 AND 1
        ),

    CONSTRAINT chk_seller_account_age
        CHECK (
            seller_account_age_days IS NULL
            OR seller_account_age_days >= 0
        )
);


CREATE TABLE risk_evaluations (
    id BIGSERIAL PRIMARY KEY,

    publication_id BIGINT NOT NULL,

    score INTEGER NOT NULL,
    level VARCHAR(20) NOT NULL,

    evaluated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_risk_evaluation_publication
        FOREIGN KEY (publication_id)
        REFERENCES publications(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_risk_score
        CHECK (score BETWEEN 0 AND 100),

    CONSTRAINT chk_risk_level
        CHECK (level IN ('LOW', 'MEDIUM', 'HIGH'))
);


CREATE TABLE risk_factors (
    id BIGSERIAL PRIMARY KEY,

    evaluation_id BIGINT NOT NULL,

    code VARCHAR(100) NOT NULL,
    description VARCHAR(500) NOT NULL,
    score_contribution INTEGER NOT NULL,

    CONSTRAINT fk_risk_factor_evaluation
        FOREIGN KEY (evaluation_id)
        REFERENCES risk_evaluations(id)
        ON DELETE CASCADE
);


CREATE INDEX idx_risk_evaluations_publication
    ON risk_evaluations(publication_id);

CREATE INDEX idx_risk_factors_evaluation
    ON risk_factors(evaluation_id);