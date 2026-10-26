package com.nexusai.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TracingTest {

    @AfterEach
    void cleanUp() {
        Tracing.endRequest();
    }

    @Test
    void acceptsValidTraceId() {
        String supplied = "0123456789abcdef0123456789abcdef";

        String actual = Tracing.beginRequest(supplied);

        assertEquals(supplied, actual);
        assertEquals(supplied, Tracing.currentTraceId());
    }

    @Test
    void generatesIdWhenIncomingValueIsInvalid() {
        String actual = Tracing.beginRequest("invalid trace id");

        assertTrue(actual.matches("[a-f0-9]{32}"));
        assertNotEquals("invalid trace id", actual);
    }

    @Test
    void generatesDifferentIdsForSeparateRequests() {
        String first = Tracing.beginRequest(null);
        Tracing.endRequest();

        String second = Tracing.beginRequest(null);

        assertNotEquals(first, second);
    }

    @Test
    void clearsTraceContextAtEndOfRequest() {
        Tracing.beginRequest(null);

        Tracing.endRequest();

        assertEquals("no-active-trace", Tracing.currentTraceId());
    }

    @Test
    void spanCanCloseWithoutThrowing() {
        Tracing.beginRequest(null);

        assertDoesNotThrow(() -> {
            try (Tracing.Span ignored =
                         Tracing.startSpan("test.operation")) {
                // Simulate a successful operation.
            }
        });
    }
}
