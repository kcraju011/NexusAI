-- V2: Incident lifecycle and audit history

-- Bring databases initialized from older schema.sql revisions up to the
-- columns required by PostgresIncidentRepository.
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

CREATE TABLE IF NOT EXISTS incident_lifecycle (
    incident_id VARCHAR(64) PRIMARY KEY
        REFERENCES incidents(id) ON DELETE CASCADE,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    assigned_to VARCHAR(150),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_incident_lifecycle_status
        CHECK (
            status IN (
                'OPEN',
                'ACKNOWLEDGED',
                'INVESTIGATING',
                'RESOLVED'
            )
        )
);

CREATE TABLE IF NOT EXISTS incident_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    incident_id VARCHAR(64) NOT NULL
        REFERENCES incidents(id) ON DELETE CASCADE,
    from_status VARCHAR(30),
    to_status VARCHAR(30) NOT NULL,
    actor VARCHAR(150) NOT NULL,
    note TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_history_to_status
        CHECK (
            to_status IN (
                'OPEN',
                'ACKNOWLEDGED',
                'INVESTIGATING',
                'RESOLVED'
            )
        ),
    CONSTRAINT chk_history_from_status
        CHECK (
            from_status IS NULL OR from_status IN (
                'OPEN',
                'ACKNOWLEDGED',
                'INVESTIGATING',
                'RESOLVED'
            )
        )
);

CREATE INDEX IF NOT EXISTS idx_incident_history_incident_time
    ON incident_history(incident_id, created_at DESC);

INSERT INTO incident_lifecycle (incident_id)
SELECT id FROM incidents
ON CONFLICT (incident_id) DO NOTHING;

INSERT INTO incident_history (
    incident_id, from_status, to_status, actor, note
)
SELECT lifecycle.incident_id, NULL, lifecycle.status,
       'system', 'Initial lifecycle state migrated'
FROM incident_lifecycle lifecycle
WHERE NOT EXISTS (
    SELECT 1
    FROM incident_history history
    WHERE history.incident_id = lifecycle.incident_id
      AND history.from_status IS NULL
);

-- Deduplication metadata

CREATE TABLE IF NOT EXISTS incident_fingerprints (
    fingerprint VARCHAR(64) PRIMARY KEY,
    incident_id VARCHAR(64) NOT NULL
        REFERENCES incidents(id) ON DELETE CASCADE,
    occurrence_count BIGINT NOT NULL DEFAULT 1,
    first_seen TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT chk_occurrence_count
        CHECK (occurrence_count >= 1)
);

CREATE INDEX IF NOT EXISTS idx_fingerprint_incident
    ON incident_fingerprints(incident_id);

-- Trace correlation metadata; an incident can have multiple traces.

CREATE TABLE IF NOT EXISTS incident_correlations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    incident_id VARCHAR(64) NOT NULL
        REFERENCES incidents(id) ON DELETE CASCADE,
    trace_id VARCHAR(32) NOT NULL,
    service_name VARCHAR(150) NOT NULL,
    operation_name VARCHAR(200) NOT NULL,
    error_type VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    linked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_trace_id_length
        CHECK (trace_id ~ '^[a-fA-F0-9]{32}$')
);

CREATE INDEX IF NOT EXISTS idx_correlations_incident
    ON incident_correlations(incident_id);

CREATE INDEX IF NOT EXISTS idx_correlations_trace
    ON incident_correlations(trace_id);
