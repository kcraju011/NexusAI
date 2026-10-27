
package com.nexusai.core;

import io.opentelemetry.api.trace.Span;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
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
                    INSERT INTO incidents (id, description, severity)
                    VALUES (?, ?, ?)
                    """;

            try (Connection connection = getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(sql)) {

                statement.setString(1, incident.getId());
                statement.setString(2, incident.getDescription());
                statement.setString(
                        3, incident.getSeverity().name());

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
                            SELECT id, service_id, description, severity
                                FROM incidents
                                WHERE id = ?
                                """;

                        try (Connection connection = getConnection();
                             PreparedStatement statement =
                                     connection.prepareStatement(sql)) {

                            statement.setString(1, id);

                            try (ResultSet result = statement.executeQuery()) {
                                if (!result.next()) {
                                    return Optional.empty();
                                }

                                Incident incident = new Incident(
                                        result.getString("id"),
                                        new Service(
                                                result.getString("service_id"),
                                                result.getString("service_id")),
                                        result.getString("description"),
                                        Incident.Severity.valueOf(
                                                result.getString("severity"))
                                );

                                return Optional.of(incident);
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

   public List<Incident> findAll() throws SQLException {

    String sql = """
            SELECT id, service_id, description, severity
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
            incidents.add(new Incident(
                    result.getString("id"),
                new Service(
                    result.getString("service_id"),
                    result.getString("service_id")),
                    result.getString("description"),
                    Incident.Severity.valueOf(
                            result.getString("severity"))
            ));
        }
    }

    return incidents;
} 
}
