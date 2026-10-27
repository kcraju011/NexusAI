package com.nexusai.core;

import java.sql.SQLException;
import java.util.UUID;

import static com.nexusai.core.Incident.Severity.HIGH;

public class PostgresIncidentRepositoryTest {

    private static void check(
            boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws Exception {

                if (isDatabaseConfigurationAbsent()) {
                        System.out.println(
                                        "SKIPPED: Set NEXUS_DB_URL, NEXUS_DB_USER, and "
                                                        + "NEXUS_DB_PASSWORD to run PostgreSQL tests.");
                        return;
                }

        String url = requiredEnv("NEXUS_DB_URL");
        String username = requiredEnv("NEXUS_DB_USER");
        String password = requiredEnv("NEXUS_DB_PASSWORD");

        Telemetry telemetry = new Telemetry(System.getenv().getOrDefault(
                "OTEL_EXPORTER_OTLP_ENDPOINT",
                "http://localhost:4317"));
        PostgresIncidentRepository repository =
                new PostgresIncidentRepository(
                        url, username, password, telemetry);

        String id = "TEST-" + UUID.randomUUID();

        Incident incident = new Incident(
                id,
                new Service("S001", "Test service"),
                "Database persistence test",
                HIGH
        );

        try {
            repository.save(incident);

            var found = repository.findById(id);

            check(found.isPresent(),
                    "Saved incident should exist");

            check(found.get().getDescription().equals(
                            "Database persistence test"),
                    "Description should be preserved");

            check(found.get().getService().getId().equals("S001"),
                    "Service ID should be preserved");

            check(found.get().getSeverity() == HIGH,
                    "Severity should be preserved");

            boolean duplicateRejected = false;

            try {
                repository.save(incident);
            } catch (SQLException exception) {
                duplicateRejected =
                        "23505".equals(exception.getSQLState());
            }

            check(duplicateRejected,
                    "Duplicate primary key must be rejected");

            check(repository.deleteById(id),
                    "Delete should remove the record");

            check(repository.findById(id).isEmpty(),
                    "Deleted record should no longer exist");

            check(!repository.deleteById(id),
                    "Deleting an absent record should return false");

            System.out.println(
                    "All PostgreSQL repository tests passed!");

        } finally {
            // Ensure the test record is cleaned up after a failure.
            repository.deleteById(id);
            telemetry.close();
        }
    }

        private static boolean isDatabaseConfigurationAbsent() {
                return isBlank(System.getenv("NEXUS_DB_URL"))
                                && isBlank(System.getenv("NEXUS_DB_USER"))
                                && isBlank(System.getenv("NEXUS_DB_PASSWORD"));
        }

        private static boolean isBlank(String value) {
                return value == null || value.isBlank();
        }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Missing environment variable: " + name);
        }

        return value;
    }
}