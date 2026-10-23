package com.nexusai.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReliabilityMonitorTest {

    @Test
    void firesAndResolvesErrorRateAlert() {
        MetricsRegistry metrics = new MetricsRegistry();
        List<ReliabilityMonitor.AlertEvent> events = new ArrayList<>();

        ReliabilityMonitor monitor = new ReliabilityMonitor(
                metrics, events::add, 5, 0.20, 0.50
        );

        // First interval: 3 errors out of 10 requests = 30%.
        for (int i = 0; i < 7; i++) {
            metrics.recordRequest(200, 10_000_000);
        }
        for (int i = 0; i < 3; i++) {
            metrics.recordRequest(500, 10_000_000);
        }

        monitor.evaluate();

        assertEquals(1, events.size());
        assertEquals("high-error-rate", events.get(0).ruleId());
        assertEquals(
                ReliabilityMonitor.State.FIRING,
                events.get(0).state()
        );
        assertTrue(monitor.isActive("high-error-rate"));

        // Second interval: all successful, fast requests.
        for (int i = 0; i < 10; i++) {
            metrics.recordRequest(200, 10_000_000);
        }

        monitor.evaluate();

        assertEquals(2, events.size());
        assertEquals(
                ReliabilityMonitor.State.RESOLVED,
                events.get(1).state()
        );
        assertFalse(monitor.isActive("high-error-rate"));
    }

    @Test
    void doesNotFireForInsufficientTraffic() {
        MetricsRegistry metrics = new MetricsRegistry();
        List<ReliabilityMonitor.AlertEvent> events = new ArrayList<>();

        ReliabilityMonitor monitor = new ReliabilityMonitor(
                metrics, events::add, 5, 0.10, 0.50
        );

        metrics.recordRequest(500, 10_000_000);
        metrics.recordRequest(500, 10_000_000);

        monitor.evaluate();

        assertTrue(events.isEmpty());
    }

    @Test
    void doesNotRepeatTheSameFiringAlert() {
        MetricsRegistry metrics = new MetricsRegistry();
        List<ReliabilityMonitor.AlertEvent> events = new ArrayList<>();

        ReliabilityMonitor monitor = new ReliabilityMonitor(
                metrics, events::add, 2, 0.10, 0.50
        );

        for (int i = 0; i < 2; i++) {
            metrics.recordRequest(500, 10_000_000);
        }
        monitor.evaluate();

        // Continue failing: the rule is already active.
        for (int i = 0; i < 2; i++) {
            metrics.recordRequest(500, 10_000_000);
        }
        monitor.evaluate();

        long firingCount = events.stream()
                .filter(event ->
                        event.state() == ReliabilityMonitor.State.FIRING)
                .count();

        assertEquals(1, firingCount);
    }

    @Test
    void firesHighLatencyAlert() {
        MetricsRegistry metrics = new MetricsRegistry();
        List<ReliabilityMonitor.AlertEvent> events = new ArrayList<>();

        ReliabilityMonitor monitor = new ReliabilityMonitor(
                metrics, events::add, 5, 0.90, 0.20
        );

        // 4 requests exceed 250 ms; 1 request is fast.
        for (int i = 0; i < 4; i++) {
            metrics.recordRequest(200, 500_000_000);
        }
        metrics.recordRequest(200, 10_000_000);

        monitor.evaluate();

        assertTrue(events.stream().anyMatch(event ->
                event.ruleId().equals("high-latency")
                        && event.state() == ReliabilityMonitor.State.FIRING
        ));
    }
}
