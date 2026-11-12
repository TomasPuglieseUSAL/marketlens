ALTER TABLE risk_evaluations
    ADD COLUMN coverage_status VARCHAR(20),
    ADD COLUMN available_factors INTEGER,
    ADD COLUMN total_factors INTEGER;

ALTER TABLE risk_evaluations
    ADD CONSTRAINT chk_risk_evaluation_coverage_status
    CHECK (
        coverage_status IS NULL
        OR coverage_status IN (
            'COMPLETE',
            'PARTIAL',
            'INSUFFICIENT'
        )
    );

ALTER TABLE risk_evaluations
    ADD CONSTRAINT chk_risk_evaluation_coverage_counts
    CHECK (
        (
            coverage_status IS NULL
            AND available_factors IS NULL
            AND total_factors IS NULL
        )
        OR
        (
            coverage_status IS NOT NULL
            AND available_factors IS NOT NULL
            AND total_factors IS NOT NULL
            AND total_factors > 0
            AND available_factors >= 0
            AND available_factors <= total_factors
            AND (
                (coverage_status = 'COMPLETE'
                    AND available_factors = total_factors)
                OR
                (coverage_status = 'PARTIAL'
                    AND available_factors > 0
                    AND available_factors < total_factors)
                OR
                (coverage_status = 'INSUFFICIENT'
                    AND available_factors = 0)
            )
        )
    );