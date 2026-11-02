package com.nexusai.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IncidentCorrelationServiceTest {

    private final IncidentCorrelationService service =
            new IncidentCorrelationService();

    private final String traceId =
            "0123456789abcdef0123456789abcdef";

    @Test
    void shouldLinkIncidentToTrace() {
        var result = service.link(
                "INC-1001",
                traceId,
                "nexusai-api",
                "postgres.incident.find_by_id",
                "SQLException",
                "Database timeout"
        );

        assertEquals("INC-1001", result.incidentId());
        assertEquals(traceId, result.traceId());
        assertEquals("nexusai-api", result.serviceName());
        assertEquals(
                "postgres.incident.find_by_id",
                result.operationName());
        assertEquals("SQLException", result.errorType());
        assertEquals("Database timeout", result.message());
        assertNotNull(result.linkedAt());
    }

    @Test
    void shouldFindExistingCorrelation() {
        service.link(
                "INC-1002", traceId,
                "nexusai-api", "http.request",
                "RuntimeException", "Request failed"
        );

        var result = service.findByIncidentId("INC-1002");

        assertTrue(result.isPresent());
        assertEquals("INC-1002", result.get().incidentId());
    }

    @Test
    void shouldRejectInvalidTraceId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.link(
                        "INC-1003",
                        "not-a-valid-trace-id",
                        "nexusai-api",
                        "http.request",
                        "RuntimeException",
                        "Request failed"
                )
        );
    }
}
