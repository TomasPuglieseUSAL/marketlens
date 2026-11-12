CREATE TABLE reports (
    id BIGSERIAL PRIMARY KEY,

    publication_id BIGINT NOT NULL,

    reason VARCHAR(50) NOT NULL,

    description VARCHAR(1000),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_report_publication
        FOREIGN KEY (publication_id)
        REFERENCES publications(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_report_reason CHECK (
        reason IN (
            'SUSPICIOUS_PRICE',
            'SUSPICIOUS_SELLER',
            'SUSPICIOUS_DESCRIPTION',
            'PAYMENT_OUTSIDE_PLATFORM',
            'POSSIBLE_IMPERSONATION',
            'OTHER'
        )
    )
);

CREATE INDEX idx_reports_publication
    ON reports(publication_id);

CREATE INDEX idx_reports_created_at
    ON reports(created_at);