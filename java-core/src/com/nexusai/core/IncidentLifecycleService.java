
package com.nexusai.core;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class IncidentLifecycleService {

    public enum Status {
        OPEN,
        ACKNOWLEDGED,
        INVESTIGATING,
        RESOLVED
    }

    public record HistoryEvent(
            String incidentId,
            Status fromStatus,
            Status toStatus,
            String actor,
            String note,
            Instant timestamp
    ) {}

    public record IncidentState(
            String incidentId,
            Status status,
            String assignedTo,
            Instant createdAt,
            Instant updatedAt
    ) {}

    private static class Aggregate {
        private Status status = Status.OPEN;
        private String assignedTo;
        private final Instant createdAt = Instant.now();
        private Instant updatedAt = createdAt;
        private final List<HistoryEvent> history = new ArrayList<>();
    }

    private final ConcurrentHashMap<String, Aggregate> incidents =
            new ConcurrentHashMap<>();
    private final PostgresIncidentLifecycleRepository repository;

    public IncidentLifecycleService() {
        this.repository = null;
    }

    public IncidentLifecycleService(
            PostgresIncidentLifecycleRepository repository) {
        this.repository = java.util.Objects.requireNonNull(
                repository, "repository");
    }

    public IncidentState register(String incidentId) {
        requireText(incidentId, "incidentId");

        if (repository != null) {
            try {
                if (!repository.registerIfAbsent(incidentId)) {
                    throw new IllegalStateException(
                            "Incident is already registered: " + incidentId);
                }
                return requirePersistedState(incidentId);
            } catch (SQLException exception) {
                throw persistenceFailure(
                        "Failed to register incident lifecycle", exception);
            }
        }

        Aggregate aggregate = new Aggregate();
        Aggregate existing = incidents.putIfAbsent(
                incidentId,
                aggregate
        );

        if (existing != null) {
            throw new IllegalStateException(
                    "Incident is already registered: " + incidentId
            );
        }

        return toState(incidentId, aggregate);
    }

    public IncidentState acknowledge(
            String incidentId,
            String actor,
            String note
    ) {
        return transition(
                incidentId,
                Status.ACKNOWLEDGED,
                actor,
                note
        );
    }

    public IncidentState startInvestigation(
            String incidentId,
            String actor,
            String note
    ) {
        return transition(
                incidentId,
                Status.INVESTIGATING,
                actor,
                note
        );
    }

    public IncidentState resolve(
            String incidentId,
            String actor,
            String note
    ) {
        return transition(
                incidentId,
                Status.RESOLVED,
                actor,
                note
        );
    }

    private IncidentState transition(
            String incidentId,
            Status target,
            String actor,
            String note
    ) {
        requireText(incidentId, "incidentId");
        requireText(actor, "actor");
        requireText(note, "note");

        if (repository != null) {
            try {
                return toState(repository.transition(
                        incidentId, target, actor, note));
            } catch (SQLException exception) {
                throw persistenceFailure(
                        "Failed to update incident lifecycle", exception);
            }
        }

        Aggregate aggregate = incidents.get(incidentId);

        if (aggregate == null) {
            throw new IllegalArgumentException(
                    "Incident not found: " + incidentId
            );
        }

        synchronized (aggregate) {
            Status current = aggregate.status;

            if (!isAllowed(current, target)) {
                throw new IllegalStateException(
                        "Invalid transition: " + current + " -> " + target
                );
            }

            Instant now = Instant.now();

            aggregate.status = target;
            aggregate.updatedAt = now;

            if (target == Status.ACKNOWLEDGED ||
                    target == Status.INVESTIGATING) {
                aggregate.assignedTo = actor;
            }

            aggregate.history.add(
                    new HistoryEvent(
                            incidentId,
                            current,
                            target,
                            actor,
                            note,
                            now
                    )
            );

            return toState(incidentId, aggregate);
        }
    }

    private boolean isAllowed(Status current, Status target) {
        return switch (current) {
            case OPEN ->
                    target == Status.ACKNOWLEDGED ||
                    target == Status.INVESTIGATING ||
                    target == Status.RESOLVED;

            case ACKNOWLEDGED ->
                    target == Status.INVESTIGATING ||
                    target == Status.RESOLVED;

            case INVESTIGATING ->
                    target == Status.RESOLVED;

            case RESOLVED -> false;
        };
    }

    public IncidentState getState(String incidentId) {
        requireText(incidentId, "incidentId");
        if (repository != null) {
            try {
                return requirePersistedState(incidentId);
            } catch (SQLException exception) {
                throw persistenceFailure(
                        "Failed to read incident lifecycle", exception);
            }
        }

        Aggregate aggregate = incidents.get(incidentId);

        if (aggregate == null) {
            throw new IllegalArgumentException(
                    "Incident not found: " + incidentId
            );
        }

        synchronized (aggregate) {
            return toState(incidentId, aggregate);
        }
    }

    public List<HistoryEvent> getHistory(String incidentId) {
        requireText(incidentId, "incidentId");
        if (repository != null) {
            try {
                if (repository.getState(incidentId) == null) {
                    throw new IllegalArgumentException(
                            "Incident not found: " + incidentId);
                }
                return repository.getHistory(incidentId).stream()
                        .map(event -> new HistoryEvent(
                                event.incidentId(),
                                event.fromStatus(),
                                event.toStatus(),
                                event.actor(),
                                event.note(),
                                event.createdAt()))
                        .toList();
            } catch (SQLException exception) {
                throw persistenceFailure(
                        "Failed to read incident lifecycle history",
                        exception);
            }
        }

        Aggregate aggregate = incidents.get(incidentId);

        if (aggregate == null) {
            throw new IllegalArgumentException(
                    "Incident not found: " + incidentId
            );
        }

        synchronized (aggregate) {
            return List.copyOf(aggregate.history);
        }
    }

    private IncidentState toState(
            String incidentId,
            Aggregate aggregate
    ) {
        return new IncidentState(
                incidentId,
                aggregate.status,
                aggregate.assignedTo,
                aggregate.createdAt,
                aggregate.updatedAt
        );
    }

    private IncidentState requirePersistedState(String incidentId)
            throws SQLException {
        PostgresIncidentLifecycleRepository.State state =
                repository.getState(incidentId);
        if (state == null) {
            throw new IllegalArgumentException(
                    "Incident not found: " + incidentId);
        }
        return toState(state);
    }

    private IncidentState toState(
            PostgresIncidentLifecycleRepository.State state) {
        return new IncidentState(
                state.incidentId(),
                state.status(),
                state.assignedTo(),
                state.createdAt(),
                state.updatedAt());
    }

    private IllegalStateException persistenceFailure(
            String message, SQLException cause) {
        return new IllegalStateException(message, cause);
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be empty"
            );
        }
    }
}
