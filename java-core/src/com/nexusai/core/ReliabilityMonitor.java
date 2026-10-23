
package com.nexusai.core;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class ReliabilityMonitor {

    public enum State {
        FIRING,
        RESOLVED
    }

    public record AlertEvent(
            String ruleId,
            State state,
            Incident.Severity severity,
            String message,
            Instant timestamp
    ) {}

    private record RuleConfig(
            String id,
            Incident.Severity severity
    ) {}

    private static final RuleConfig ERROR_RULE =
            new RuleConfig("high-error-rate", Incident.Severity.CRITICAL);

    private static final RuleConfig LATENCY_RULE =
            new RuleConfig("high-latency", Incident.Severity.HIGH);

    private final MetricsRegistry metrics;
    private final Consumer<AlertEvent> alertSink;

    private final Map<String, Boolean> activeRules = new HashMap<>();

    private MetricsRegistry.Snapshot previous;
    private final int minimumRequests;
    private final double errorRateThreshold;
    private final double slowRequestThreshold;

    public ReliabilityMonitor(
            MetricsRegistry metrics,
            Consumer<AlertEvent> alertSink,
            int minimumRequests,
            double errorRateThreshold,
            double slowRequestThreshold
    ) {
        this.metrics = Objects.requireNonNull(metrics);
        this.alertSink = Objects.requireNonNull(alertSink);

        if (minimumRequests < 1) {
            throw new IllegalArgumentException(
                    "minimumRequests must be at least 1");
        }

        if (errorRateThreshold < 0 || errorRateThreshold > 1) {
            throw new IllegalArgumentException(
                    "errorRateThreshold must be between 0 and 1");
        }

        if (slowRequestThreshold < 0 || slowRequestThreshold > 1) {
            throw new IllegalArgumentException(
                    "slowRequestThreshold must be between 0 and 1");
        }

        this.minimumRequests = minimumRequests;
        this.errorRateThreshold = errorRateThreshold;
        this.slowRequestThreshold = slowRequestThreshold;
        this.previous = metrics.snapshot();

        activeRules.put(ERROR_RULE.id(), false);
        activeRules.put(LATENCY_RULE.id(), false);
    }

    // Call periodically, for example every 15 seconds.
    public synchronized void evaluate() {
        MetricsRegistry.Snapshot current = metrics.snapshot();

        long requests = current.totalRequests()
                - previous.totalRequests();

        long errors = current.serverErrors()
                - previous.serverErrors();

        long fastRequests = current.requestsUnder250ms()
                - previous.requestsUnder250ms();

        previous = current;

        // Avoid calculating a percentage from an empty interval.
        if (requests <= 0) {
            return;
        }

        // Defensive guard if counters reset while the monitor is running.
        if (errors < 0 || fastRequests < 0) {
            resolveIfActive(ERROR_RULE,
                    "Metrics counter reset; error-rate evaluation restarted.");
            resolveIfActive(LATENCY_RULE,
                    "Metrics counter reset; latency evaluation restarted.");
            return;
        }

        if (requests < minimumRequests) {
            // Insufficient traffic: do not claim the service is healthy
            // or unhealthy based on an unreliable sample.
            return;
        }

        double errorRate = (double) errors / requests;
        double slowRate = (double) (requests - fastRequests) / requests;

        updateRule(
                ERROR_RULE,
                errorRate > errorRateThreshold,
                String.format(
                        "HTTP server error rate is %.1f%% (threshold %.1f%%)",
                        errorRate * 100,
                        errorRateThreshold * 100
                )
        );

        updateRule(
                LATENCY_RULE,
                slowRate > slowRequestThreshold,
                String.format(
                        "%.1f%% of requests exceeded 250 ms (threshold %.1f%%)",
                        slowRate * 100,
                        slowRequestThreshold * 100
                )
        );
    }

    private void updateRule(
            RuleConfig rule,
            boolean conditionMet,
            String message
    ) {
        boolean wasActive = activeRules.get(rule.id());

        if (conditionMet && !wasActive) {
            alertSink.accept(new AlertEvent(
                    rule.id(),
                    State.FIRING,
                    rule.severity(),
                    message,
                    Instant.now()
            ));
            activeRules.put(rule.id(), true);
        } else if (!conditionMet && wasActive) {
            alertSink.accept(new AlertEvent(
                    rule.id(),
                    State.RESOLVED,
                    rule.severity(),
                    message,
                    Instant.now()
            ));
            activeRules.put(rule.id(), false);
        }
    }

    private void resolveIfActive(RuleConfig rule, String message) {
        if (activeRules.get(rule.id())) {
            alertSink.accept(new AlertEvent(
                    rule.id(),
                    State.RESOLVED,
                    rule.severity(),
                    message,
                    Instant.now()
            ));
            activeRules.put(rule.id(), false);
        }
    }

    public synchronized boolean isActive(String ruleId) {
        return activeRules.getOrDefault(ruleId, false);
    }
}
