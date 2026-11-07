
package com.nexusai.core;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

public class IncidentRecoveryService {

    public enum RecoveryStatus {
        MONITORING,
        RECOVERING,
        RESOLVED
    }

    public record RecoveryResult(
            String incidentId,
            RecoveryStatus status,
            int consecutiveSuccesses,
            String message
    ) {}

    private static class RecoveryState {
        private int consecutiveSuccesses;
        private Instant firstSuccessAt;
        private RecoveryStatus status = RecoveryStatus.MONITORING;
    }

    private final IncidentLifecycleService lifecycleService;
    private final int requiredSuccesses;
    private final Duration minimumRecoveryDuration;

    private final ConcurrentHashMap<String, RecoveryState> states =
            new ConcurrentHashMap<>();

    public IncidentRecoveryService(
            IncidentLifecycleService lifecycleService,
            int requiredSuccesses,
            Duration minimumRecoveryDuration
    ) {
        if (lifecycleService == null) {
            throw new IllegalArgumentException(
                    "Lifecycle service is required"
            );
        }

        if (requiredSuccesses < 1) {
            throw new IllegalArgumentException(
                    "Required successes must be at least 1"
            );
        }

        if (minimumRecoveryDuration == null ||
                minimumRecoveryDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "Recovery duration must not be negative"
            );
        }

        this.lifecycleService = lifecycleService;
        this.requiredSuccesses = requiredSuccesses;
        this.minimumRecoveryDuration = minimumRecoveryDuration;
    }

    public RecoveryResult recordSuccess(
            String incidentId,
            Instant observedAt
    ) {
        requireText(incidentId);
        requireTime(observedAt);
        lifecycleService.getState(incidentId);

        RecoveryState state = states.computeIfAbsent(
                incidentId,
                ignored -> new RecoveryState()
        );

        synchronized (state) {
            if (state.status == RecoveryStatus.RESOLVED) {
                return result(
                        incidentId,
                        state,
                        "Incident is already resolved"
                );
            }

            if (state.consecutiveSuccesses == 0) {
                state.firstSuccessAt = observedAt;
            }

            state.consecutiveSuccesses++;
            state.status = RecoveryStatus.RECOVERING;

            boolean enoughChecks =
                    state.consecutiveSuccesses >= requiredSuccesses;

            boolean enoughTime =
                    !observedAt.isBefore(
                            state.firstSuccessAt.plus(
                                    minimumRecoveryDuration
                            )
                    );

            if (enoughChecks && enoughTime) {
                lifecycleService.resolve(
                        incidentId,
                        "system-recovery",
                        "Automatically resolved after consecutive "
                                + "successful health checks"
                );

                state.status = RecoveryStatus.RESOLVED;

                return result(
                        incidentId,
                        state,
                        "Recovery confirmed; incident resolved"
                );
            }

            return result(
                    incidentId,
                    state,
                    "Recovery check recorded; waiting for threshold"
            );
        }
    }

    public RecoveryResult recordFailure(
            String incidentId,
            Instant observedAt
    ) {
        requireText(incidentId);
        requireTime(observedAt);
        lifecycleService.getState(incidentId);

        RecoveryState state = states.computeIfAbsent(
                incidentId,
                ignored -> new RecoveryState()
        );

        synchronized (state) {
            if (state.status == RecoveryStatus.RESOLVED) {
                return result(
                        incidentId,
                        state,
                        "Incident is already resolved; create or reopen "
                                + "an incident for a new failure"
                );
            }

            state.consecutiveSuccesses = 0;
            state.firstSuccessAt = null;
            state.status = RecoveryStatus.MONITORING;

            return result(
                    incidentId,
                    state,
                    "Failure detected; recovery sequence reset"
            );
        }
    }

    public RecoveryResult getStatus(String incidentId) {
        requireText(incidentId);

        RecoveryState state = states.get(incidentId);

        if (state == null) {
            return new RecoveryResult(
                    incidentId,
                    RecoveryStatus.MONITORING,
                    0,
                    "No recovery checks recorded"
            );
        }

        synchronized (state) {
            return result(
                    incidentId,
                    state,
                    "Current recovery state"
            );
        }
    }

    private RecoveryResult result(
            String incidentId,
            RecoveryState state,
            String message
    ) {
        return new RecoveryResult(
                incidentId,
                state.status,
                state.consecutiveSuccesses,
                message
        );
    }

    private void requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Incident ID must not be empty"
            );
        }
    }

    private void requireTime(Instant time) {
        if (time == null) {
            throw new IllegalArgumentException(
                    "Observation time is required"
            );
        }
    }
}
