-- La aplicación permite RiskLevel.INDETERMINATE cuando la cobertura
-- de evidencia no es suficiente para clasificar el riesgo como bajo.
-- V1 restringía el campo a LOW/MEDIUM/HIGH y provocaba un error
-- de integridad al persistir ese nuevo nivel.
-- No modificar V1: Flyway aplica esta migración incremental.
ALTER TABLE risk_evaluations
    DROP CONSTRAINT chk_risk_level;

ALTER TABLE risk_evaluations
    ADD CONSTRAINT chk_risk_level
    CHECK (level IN ('LOW', 'MEDIUM', 'HIGH', 'INDETERMINATE'));
