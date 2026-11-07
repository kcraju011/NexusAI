
package com.nexusai.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class IncidentRecoveryServiceTest {

    private IncidentLifecycleService lifecycle;
    private IncidentRecoveryService recovery;

    private final Instant start =
            Instant.parse("2026-11-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        lifecycle = new IncidentLifecycleService();
        lifecycle.register("INC-1001");

        recovery = new IncidentRecoveryService(
                lifecycle,
                3,
                Duration.ofSeconds(30)
        );
    }

    @Test
    void shouldNotResolveBeforeSuccessThreshold() {
        var result = recovery.recordSuccess(
                "INC-1001", start
        );

        assertEquals(
                IncidentRecoveryService.RecoveryStatus.RECOVERING,
                result.status()
        );

        assertEquals(
                IncidentLifecycleService.Status.OPEN,
                lifecycle.getState("INC-1001").status()
        );
    }

    @Test
    void shouldResolveAfterChecksAndDuration() {
        recovery.recordSuccess(
                "INC-1001", start
        );

        recovery.recordSuccess(
                "INC-1001", start.plusSeconds(15)
        );

        var result = recovery.recordSuccess(
                "INC-1001", start.plusSeconds(30)
        );

        assertEquals(
                IncidentRecoveryService.RecoveryStatus.RESOLVED,
                result.status()
        );

        assertEquals(
                IncidentLifecycleService.Status.RESOLVED,
                lifecycle.getState("INC-1001").status()
        );
    }

    @Test
    void failureShouldResetRecoverySequence() {
        recovery.recordSuccess(
                "INC-1001", start
        );

        recovery.recordSuccess(
                "INC-1001", start.plusSeconds(10)
        );

        var result = recovery.recordFailure(
                "INC-1001", start.plusSeconds(15)
        );

        assertEquals(0, result.consecutiveSuccesses());

        assertEquals(
                IncidentRecoveryService.RecoveryStatus.MONITORING,
                result.status()
        );
    }

    @Test
    void shouldRequireNewRecoverySequenceAfterFailure() {
        recovery.recordSuccess(
                "INC-1001", start
        );

        recovery.recordSuccess(
                "INC-1001", start.plusSeconds(10)
        );

        recovery.recordFailure(
                "INC-1001", start.plusSeconds(15)
        );

        var result = recovery.recordSuccess(
                "INC-1001", start.plusSeconds(45)
        );

        assertEquals(1, result.consecutiveSuccesses());

        assertEquals(
                IncidentLifecycleService.Status.OPEN,
                lifecycle.getState("INC-1001").status()
        );
    }

    @Test
    void shouldNotResolveBeforeMinimumDuration() {
        recovery.recordSuccess(
                "INC-1001", start
        );

        recovery.recordSuccess(
                "INC-1001", start.plusSeconds(1)
        );

        var result = recovery.recordSuccess(
                "INC-1001", start.plusSeconds(2)
        );

        assertEquals(
                IncidentRecoveryService.RecoveryStatus.RECOVERING,
                result.status()
        );

        assertEquals(
                IncidentLifecycleService.Status.OPEN,
                lifecycle.getState("INC-1001").status()
        );
    }

    @Test
    void shouldRemainResolvedAfterAnotherSuccess() {
        recovery.recordSuccess(
                "INC-1001", start
        );

        recovery.recordSuccess(
                "INC-1001", start.plusSeconds(15)
        );

        recovery.recordSuccess(
                "INC-1001", start.plusSeconds(30)
        );

        var result = recovery.recordSuccess(
                "INC-1001", start.plusSeconds(45)
        );

        assertEquals(
                IncidentRecoveryService.RecoveryStatus.RESOLVED,
                result.status()
        );
    }

    @Test
    void shouldRejectUnknownIncidentDuringResolution() {
        assertThrows(
                IllegalArgumentException.class,
                () -> recovery.recordSuccess(
                        "UNKNOWN", start
                )
        );
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new IncidentRecoveryService(
                        lifecycle,
                        0,
                        Duration.ofSeconds(30)
                )
        );
    }
}
