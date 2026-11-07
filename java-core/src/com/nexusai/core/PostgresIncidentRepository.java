
package com.nexusai.core;

import io.opentelemetry.api.trace.Span;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

public class PostgresIncidentRepository {

    private final String url;
    private final String username;
    private final String password;
    private final Telemetry telemetry;

    public PostgresIncidentRepository(
            String url,
            String username,
            String password,
            Telemetry telemetry) {

        this.url = url;
        this.username = username;
        this.password = password;
        this.telemetry = telemetry;
    }

    private Connection getConnection() throws SQLException {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        return DriverManager.getConnection(
                url, username, password);
    }
 
    
    public void save(Incident incident) throws SQLException {

        Span span = telemetry.startSpan("postgres.incident.save");
        try {
            String sql = """
                    INSERT INTO incidents
                        (id, service_id, description, severity,
                         occurrence_count, first_seen_at, last_seen_at,
                         fingerprint, resolved_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;

            try (Connection connection = getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(sql)) {

                statement.setString(1, incident.getId());
                statement.setString(2, incident.getService().getId());
                statement.setString(3, incident.getDescription());
                statement.setString(4, incident.getSeverity().name());
                statement.setLong(5, incident.getOccurrenceCount());
                statement.setTimestamp(
                        6, Timestamp.from(incident.getFirstSeen()));
                statement.setTimestamp(
                        7, Timestamp.from(incident.getLastSeen()));
                statement.setString(8, incident.getFingerprint());
                setNullableTimestamp(statement, 9, incident.getResolvedAt());

                statement.executeUpdate();
            }
        } catch (SQLException | RuntimeException exception) {
            Telemetry.recordError(span, exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    public Optional<Incident> findById(String id)
            throws SQLException {

        Span telemetrySpan =
                telemetry.startSpan("postgres.incident.find_by_id");
        try {
            try {
                try (Tracing.Span span =
                     Tracing.startSpan("postgres.incident.find_by_id")) {
                    try {
                        String sql = """
                            SELECT id, service_id, description, severity,
                                   occurrence_count, first_seen_at,
                                   last_seen_at, fingerprint, resolved_at
                                FROM incidents
                                WHERE id = ?
                                """;

                        try (Connection connection = getConnection();
                             PreparedStatement statement =
                                     connection.prepareStatement(sql)) {

                            statement.setString(1, id);

                            try (ResultSet result = statement.executeQuery()) {
                                return result.next()
                                        ? Optional.of(readIncident(result))
                                        : Optional.empty();
                            }
                        }
                    } catch (SQLException | RuntimeException exception) {
                        span.fail(exception);
                        throw exception;
                    }
                }
            } catch (SQLException | RuntimeException exception) {
                Telemetry.recordError(telemetrySpan, exception);
                throw exception;
            }
        } finally {
            telemetrySpan.end();
        }
    }

    public long count() throws SQLException {

        String sql = "SELECT COUNT(*) FROM incidents";

        try (Connection connection = getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            result.next();
            return result.getLong(1);
        }
    }

    public boolean deleteById(String id) throws SQLException {

        String sql = "DELETE FROM incidents WHERE id = ?";

        try (Connection connection = getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, id);
            return statement.executeUpdate() > 0;
        }
    }

    public boolean updateOccurrence(
            String id,
            long occurrenceCount,
            Instant lastSeen) throws SQLException {
        String sql = """
                UPDATE incidents
                SET occurrence_count = ?, last_seen_at = ?
                WHERE id = ? AND resolved_at IS NULL
                """;

        try (Connection connection = getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            statement.setLong(1, occurrenceCount);
            statement.setTimestamp(2, Timestamp.from(lastSeen));
            statement.setString(3, id);
            return statement.executeUpdate() > 0;
        }
    }

    public boolean markResolved(String id, Instant resolvedAt)
            throws SQLException {
        String sql = """
                UPDATE incidents
                SET resolved_at = ?
                WHERE id = ? AND resolved_at IS NULL
                """;

        try (Connection connection = getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {
            statement.setTimestamp(1, Timestamp.from(resolvedAt));
            statement.setString(2, id);
            return statement.executeUpdate() > 0;
        }
    }

   public List<Incident> findAll() throws SQLException {

    String sql = """
            SELECT id, service_id, description, severity,
                   occurrence_count, first_seen_at, last_seen_at,
                   fingerprint, resolved_at
            FROM incidents
            ORDER BY created_at DESC, id ASC
            LIMIT 100
            """;

    List<Incident> incidents = new ArrayList<>();

    try (Connection connection = getConnection();
         PreparedStatement statement =
                 connection.prepareStatement(sql);
         ResultSet result = statement.executeQuery()) {

        while (result.next()) {
            incidents.add(readIncident(result));
        }
    }

    return incidents;
}

    private Incident readIncident(ResultSet result) throws SQLException {
        Timestamp resolvedAt = result.getTimestamp("resolved_at");
        return new Incident(
                result.getString("id"),
                new Service(
                result.getString("service_id"),
                result.getString("service_id")),
                result.getString("description"),
                Incident.Severity.valueOf(result.getString("severity")),
                result.getString("fingerprint"),
                result.getLong("occurrence_count"),
                result.getTimestamp("first_seen_at").toInstant(),
                result.getTimestamp("last_seen_at").toInstant(),
                resolvedAt == null ? null : resolvedAt.toInstant()
        );
    }

    private void setNullableTimestamp(
            PreparedStatement statement, int index, Instant value)
            throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            statement.setTimestamp(index, Timestamp.from(value));
        }
    }
}
