package com.nexusai.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PostgresIncidentLifecycleRepository {

    public record State(
            String incidentId,
            IncidentLifecycleService.Status status,
            String assignedTo,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record History(
            long id,
            String incidentId,
            IncidentLifecycleService.Status fromStatus,
            IncidentLifecycleService.Status toStatus,
            String actor,
            String note,
            Instant createdAt
    ) {}

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public PostgresIncidentLifecycleRepository(
            String jdbcUrl,
            String username,
            String password
    ) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    public void register(String incidentId) throws SQLException {
        registerIfAbsent(incidentId);
    }

    public boolean registerIfAbsent(String incidentId) throws SQLException {
        requireText(incidentId, "incidentId");

        String insertLifecycle = """
                INSERT INTO incident_lifecycle (incident_id)
                VALUES (?)
                ON CONFLICT (incident_id) DO NOTHING
                RETURNING incident_id
                """;
        String insertHistory = """
                INSERT INTO incident_history (
                    incident_id, from_status, to_status, actor, note
                )
                VALUES (?, NULL, 'OPEN', 'system', 'Incident registered')
                """;

        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                boolean inserted;
                try (PreparedStatement statement =
                             connection.prepareStatement(insertLifecycle)) {
                    statement.setString(1, incidentId);
                    try (ResultSet result = statement.executeQuery()) {
                        inserted = result.next();
                    }
                }

                if (inserted) {
                    try (PreparedStatement statement =
                                 connection.prepareStatement(insertHistory)) {
                        statement.setString(1, incidentId);
                        statement.executeUpdate();
                    }
                }
                connection.commit();
                return inserted;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public State transition(
            String incidentId,
            IncidentLifecycleService.Status target,
            String actor,
            String note
    ) throws SQLException {
        requireText(incidentId, "incidentId");
        Objects.requireNonNull(target, "target");
        requireText(actor, "actor");
        requireText(note, "note");

        String lockSql = """
                SELECT status, assigned_to
                FROM incident_lifecycle
                WHERE incident_id = ?
                FOR UPDATE
                """;
        String updateSql = """
                UPDATE incident_lifecycle
                SET status = ?,
                    assigned_to = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE incident_id = ?
                """;
        String historySql = """
                INSERT INTO incident_history (
                    incident_id, from_status, to_status, actor, note
                )
                VALUES (?, ?, ?, ?, ?)
                """;

        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                IncidentLifecycleService.Status current;
                String assignedTo;
                try (PreparedStatement statement =
                             connection.prepareStatement(lockSql)) {
                    statement.setString(1, incidentId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException(
                                    "Incident lifecycle not found: "
                                            + incidentId);
                        }
                        current = IncidentLifecycleService.Status.valueOf(
                                result.getString("status"));
                        assignedTo = result.getString("assigned_to");
                    }
                }

                if (!isAllowed(current, target)) {
                    throw new IllegalStateException(
                            "Invalid transition: " + current + " -> " + target);
                }

                if (target
                                == IncidentLifecycleService.Status.ACKNOWLEDGED
                        || target
                                == IncidentLifecycleService.Status.INVESTIGATING) {
                    assignedTo = actor;
                }

                try (PreparedStatement statement =
                             connection.prepareStatement(updateSql)) {
                    statement.setString(1, target.name());
                    statement.setString(2, assignedTo);
                    statement.setString(3, incidentId);
                    if (statement.executeUpdate() != 1) {
                        throw new SQLException(
                                "Lifecycle state update affected no row: "
                                        + incidentId);
                    }
                }

                try (PreparedStatement statement =
                             connection.prepareStatement(historySql)) {
                    statement.setString(1, incidentId);
                    statement.setString(2, current.name());
                    statement.setString(3, target.name());
                    statement.setString(4, actor);
                    statement.setString(5, note);
                    statement.executeUpdate();
                }

                State state = readState(connection, incidentId);
                connection.commit();
                return state;
            } catch (SQLException | RuntimeException exception) {
                rollback(connection, exception);
                throw exception;
            }
        }
    }

    public State getState(String incidentId) throws SQLException {
        requireText(incidentId, "incidentId");
        try (Connection connection = connect()) {
            return readState(connection, incidentId);
        }
    }

    private State readState(Connection connection, String incidentId)
            throws SQLException {
        String sql = """
                SELECT incident_id, status, assigned_to,
                       created_at, updated_at
                FROM incident_lifecycle
                WHERE incident_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, incidentId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new State(
                        result.getString("incident_id"),
                        IncidentLifecycleService.Status.valueOf(
                                result.getString("status")),
                        result.getString("assigned_to"),
                        result.getTimestamp("created_at").toInstant(),
                        result.getTimestamp("updated_at").toInstant());
            }
        }
    }

    public List<History> getHistory(String incidentId) throws SQLException {
        requireText(incidentId, "incidentId");
        String sql = """
                SELECT id, incident_id, from_status, to_status,
                       actor, note, created_at
                FROM incident_history
                WHERE incident_id = ?
                ORDER BY created_at ASC, id ASC
                """;
        List<History> history = new ArrayList<>();
        try (Connection connection = connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, incidentId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String fromStatus = result.getString("from_status");
                    history.add(new History(
                            result.getLong("id"),
                            result.getString("incident_id"),
                            fromStatus == null
                                    ? null
                                    : IncidentLifecycleService.Status.valueOf(
                                            fromStatus),
                            IncidentLifecycleService.Status.valueOf(
                                    result.getString("to_status")),
                            result.getString("actor"),
                            result.getString("note"),
                            result.getTimestamp("created_at").toInstant()));
                }
            }
        }
        return List.copyOf(history);
    }

    private boolean isAllowed(
            IncidentLifecycleService.Status current,
            IncidentLifecycleService.Status target) {
        return switch (current) {
            case OPEN ->
                    target == IncidentLifecycleService.Status.ACKNOWLEDGED
                            || target
                                    == IncidentLifecycleService.Status.INVESTIGATING
                            || target
                                    == IncidentLifecycleService.Status.RESOLVED;
            case ACKNOWLEDGED ->
                    target
                            == IncidentLifecycleService.Status.INVESTIGATING
                            || target
                                    == IncidentLifecycleService.Status.RESOLVED;
            case INVESTIGATING ->
                    target == IncidentLifecycleService.Status.RESOLVED;
            case RESOLVED -> false;
        };
    }

    private void rollback(Connection connection, Exception original) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            original.addSuppressed(rollbackFailure);
        }
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be empty");
        }
    }
}
