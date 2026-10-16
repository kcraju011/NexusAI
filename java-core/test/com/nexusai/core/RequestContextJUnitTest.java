package com.nexusai.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RequestContextJUnitTest {

    @AfterEach
    void cleanUp() {
        RequestContext.clear();
    }

    @Test
    void generatesRequestIdWhenNoneProvided() {
        String id = RequestContext.start(null);

        assertNotNull(id);
        assertFalse(id.isBlank());
        assertEquals(id, RequestContext.getRequestId());
    }

    @Test
    void acceptsSafeRequestId() {
        assertEquals(
                "test-request-123",
                RequestContext.start("test-request-123")
        );
    }

    @Test
    void replacesUnsafeRequestId() {
        String id = RequestContext.start("invalid request\nvalue");

        assertFalse(id.contains("\n"));
        assertNotEquals("invalid request\nvalue", id);
    }

    @Test
    void clearRemovesRequestContext() {
        RequestContext.start("test-request-123");
        RequestContext.clear();

        assertNull(RequestContext.getRequestId());
    }
}