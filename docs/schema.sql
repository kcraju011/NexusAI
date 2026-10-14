CREATE TABLE IF NOT EXISTS incidents (
    id          VARCHAR(64) PRIMARY KEY,
    service_id  VARCHAR(64) NOT NULL DEFAULT 'S001',
    description VARCHAR(1000) NOT NULL,
    severity    VARCHAR(10) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT incidents_severity_check
        CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),

    CONSTRAINT incidents_description_not_empty
        CHECK (LENGTH(TRIM(description)) > 0)
);

ALTER TABLE incidents
    ADD COLUMN IF NOT EXISTS service_id
        VARCHAR(64) NOT NULL DEFAULT 'S001';

CREATE INDEX IF NOT EXISTS idx_incidents_severity
    ON incidents (severity);

CREATE INDEX IF NOT EXISTS idx_incidents_created_at
    ON incidents (created_at DESC);