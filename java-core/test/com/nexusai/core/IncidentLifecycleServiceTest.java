
package com.nexusai.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IncidentLifecycleServiceTest {

    private IncidentLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new IncidentLifecycleService();
        service.register("INC-1001");
    }

    @Test
    void newIncidentShouldBeOpen() {
        var state = service.getState("INC-1001");

        assertEquals(
                IncidentLifecycleService.Status.OPEN,
                state.status()
        );
    }

    @Test
    void shouldAcknowledgeIncident() {
        var state = service.acknowledge(
                "INC-1001", "raju", "Taking ownership"
        );

        assertEquals(
                IncidentLifecycleService.Status.ACKNOWLEDGED,
                state.status()
        );

        assertEquals("raju", state.assignedTo());
    }

    @Test
    void shouldStartInvestigationDirectly() {
        var state = service.startInvestigation(
                "INC-1001", "engineer1", "Investigating database"
        );

        assertEquals(
                IncidentLifecycleService.Status.INVESTIGATING,
                state.status()
        );
    }

    @Test
    void shouldCompleteFullLifecycle() {
        service.acknowledge(
                "INC-1001", "raju", "Acknowledged"
        );

        service.startInvestigation(
                "INC-1001", "raju", "Root cause analysis"
        );

        var resolved = service.resolve(
                "INC-1001", "raju", "Database recovered"
        );

        assertEquals(
                IncidentLifecycleService.Status.RESOLVED,
                resolved.status()
        );
    }

    @Test
    void shouldRejectInvalidTransition() {
        service.resolve(
                "INC-1001", "raju", "Resolved"
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.acknowledge(
                        "INC-1001", "raju", "Try again"
                )
        );
    }

    @Test
    void shouldRecordStatusHistory() {
        service.acknowledge(
                "INC-1001", "raju", "Acknowledged"
        );

        service.startInvestigation(
                "INC-1001", "raju", "Investigating"
        );

        var history = service.getHistory("INC-1001");

        assertEquals(2, history.size());

        assertEquals(
                IncidentLifecycleService.Status.OPEN,
                history.get(0).fromStatus()
        );

        assertEquals(
                IncidentLifecycleService.Status.ACKNOWLEDGED,
                history.get(0).toStatus()
        );
    }

    @Test
    void shouldRejectUnknownIncident() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.getState("UNKNOWN")
        );
    }

    @Test
    void shouldReturnImmutableHistory() {
        service.acknowledge(
                "INC-1001", "raju", "Acknowledged"
        );

        var history = service.getHistory("INC-1001");

        assertThrows(
                UnsupportedOperationException.class,
                () -> history.clear()
        );
    }

    @Test
    void shouldRejectDuplicateRegistration() {
        assertThrows(
                IllegalStateException.class,
                () -> service.register("INC-1001")
        );
    }
}
