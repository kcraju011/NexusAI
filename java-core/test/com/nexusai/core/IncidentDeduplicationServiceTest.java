
package com.nexusai.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class IncidentDeduplicationServiceTest {

    private final IncidentDeduplicationService service =
            new IncidentDeduplicationService();

    private IncidentDeduplicationService.Failure failure() {
        return new IncidentDeduplicationService.Failure(
                "nexusai-api",
                "postgres.incident.find_by_id",
                "SQLException",
                "DB_CONNECTION_FAILURE"
        );
    }

    @Test
    void firstFailureShouldCreateNewIncident() {
        var result = service.detect(failure());

        assertTrue(result.newIncident());
        assertEquals(1, result.occurrenceCount());
        assertNotNull(result.incidentId());
    }

    @Test
    void repeatedFailureShouldReuseIncident() {
        var first = service.detect(failure());
        var second = service.detect(failure());

        assertTrue(first.newIncident());
        assertFalse(second.newIncident());
        assertEquals(first.incidentId(), second.incidentId());
        assertEquals(2, second.occurrenceCount());
    }

    @Test
    void normalizedValuesShouldProduceSameFingerprint() {
        var first = failure();

        var second =
                new IncidentDeduplicationService.Failure(
                        " NEXUSAI-API ",
                        "POSTGRES.INCIDENT.FIND_BY_ID",
                        "SQLException",
                        "db_connection_failure"
                );

        assertEquals(
                service.createFingerprint(first),
                service.createFingerprint(second)
        );
    }

    @Test
    void differentErrorCodesShouldProduceDifferentIncidents() {
        var first = service.detect(failure());

        var second = service.detect(
                new IncidentDeduplicationService.Failure(
                        "nexusai-api",
                        "postgres.incident.find_by_id",
                        "SQLException",
                        "DB_AUTHENTICATION_FAILURE"
                )
        );

        assertNotEquals(first.incidentId(), second.incidentId());
    }

    @Test
    void resolvingFingerprintShouldAllowNewIncident() {
        var first = service.detect(failure());

        assertTrue(service.resolve(first.fingerprint()));

        var second = service.detect(failure());

        assertTrue(second.newIncident());
        assertNotEquals(first.incidentId(), second.incidentId());
    }

    @Test
    void concurrentFailuresShouldShareOneIncident() throws Exception {
        int threadCount = 50;

        ExecutorService executor =
                Executors.newFixedThreadPool(10);

        try {
            List<Callable<IncidentDeduplicationService.Detection>> tasks =
                    new ArrayList<>();

            for (int i = 0; i < threadCount; i++) {
                tasks.add(() -> service.detect(failure()));
            }

            List<Future<IncidentDeduplicationService.Detection>> results =
                    executor.invokeAll(tasks);

            String expectedId = null;

            for (Future<IncidentDeduplicationService.Detection> future
                    : results) {
                var detection = future.get();

                if (expectedId == null) {
                    expectedId = detection.incidentId();
                }

                assertEquals(expectedId, detection.incidentId());
            }

            var finalResult = service.detect(failure());

            assertEquals(51, finalResult.occurrenceCount());
            assertEquals(1, service.activeFailureCount());

        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectMissingFailureFields() {
        var invalid = new IncidentDeduplicationService.Failure(
                "nexusai-api",
                "",
                "SQLException",
                "DB_CONNECTION_FAILURE"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.detect(invalid)
        );
    }
}
