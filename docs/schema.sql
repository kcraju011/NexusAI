CREATE TABLE IF NOT EXISTS incidents (
    id          VARCHAR(64) PRIMARY KEY,
    service_id  VARCHAR(64) NOT NULL DEFAULT 'S001',
    description VARCHAR(1000) NOT NULL,
    severity    VARCHAR(10) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    occurrence_count BIGINT NOT NULL DEFAULT 1,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fingerprint VARCHAR(64),
    resolved_at TIMESTAMPTZ,

    CONSTRAINT incidents_severity_check
        CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),

    CONSTRAINT incidents_description_not_empty
        CHECK (LENGTH(TRIM(description)) > 0)
);

ALTER TABLE incidents
    ADD COLUMN IF NOT EXISTS service_id
        VARCHAR(64) NOT NULL DEFAULT 'S001';

ALTER TABLE incidents
    ADD COLUMN IF NOT EXISTS occurrence_count
        BIGINT NOT NULL DEFAULT 1;

ALTER TABLE incidents
    ADD COLUMN IF NOT EXISTS first_seen_at
        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE incidents
    ADD COLUMN IF NOT EXISTS last_seen_at
        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE incidents
    ADD COLUMN IF NOT EXISTS fingerprint VARCHAR(64);

ALTER TABLE incidents
    ADD COLUMN IF NOT EXISTS resolved_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_incidents_severity
    ON incidents (severity);

CREATE INDEX IF NOT EXISTS idx_incidents_created_at
    ON incidents (created_at DESC);