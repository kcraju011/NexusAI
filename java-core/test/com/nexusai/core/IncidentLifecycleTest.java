package com.nexusai.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IncidentLifecycleTest {

    @Test
    void tracksOccurrencesAndResolutionTime() {
        Instant firstSeen = Instant.parse("2026-11-08T00:00:00Z");
        Instant lastSeen = firstSeen.plusSeconds(30);
        Instant resolvedAt = lastSeen.plusSeconds(60);
        Incident incident = new Incident(
                "INC-1",
                new Service("S001", "nexusai-api"),
                "DB_CONNECTION_FAILURE",
                Incident.Severity.HIGH,
                "fingerprint",
                1,
                firstSeen,
                firstSeen,
                null);

        incident.recordOccurrence(2, lastSeen);
        incident.resolve(resolvedAt);

        assertEquals(2, incident.getOccurrenceCount());
        assertEquals(firstSeen, incident.getFirstSeen());
        assertEquals(lastSeen, incident.getLastSeen());
        assertEquals(resolvedAt, incident.getResolvedAt());
        assertNotNull(incident.getFingerprint());
    }
}
