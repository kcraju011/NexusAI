package com.nexusai.core;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ApiMain {

    public static void main(String[] args) throws IOException {

        String url = requiredEnv("NEXUS_DB_URL");
        String username = requiredEnv("NEXUS_DB_USER");
        String password = requiredEnv("NEXUS_DB_PASSWORD");
        String otlpEndpoint = System.getenv().getOrDefault(
                "OTEL_EXPORTER_OTLP_ENDPOINT",
                "http://localhost:4317");
        Telemetry telemetry = new Telemetry(otlpEndpoint);

        IncidentManager manager = new IncidentManager();
        AuditLogger auditLogger =
        new AuditLogger(java.nio.file.Path.of("logs", "audit.jsonl"));

        ServiceGraph graph = new ServiceGraph();

        graph.addService("S001");
        graph.addService("S002");
        graph.addService("S003");
        graph.addService("S004");

        graph.addDependency("S001", "S002");
        graph.addDependency("S002", "S003");
        graph.addDependency("S001", "S004");

        PostgresIncidentRepository repository =
                new PostgresIncidentRepository(
                        url, username, password, telemetry);

        MetricsRegistry metrics = new MetricsRegistry();
        Map<String, String> activeAlertIncidents = new HashMap<>();
        ReliabilityMonitor monitor = new ReliabilityMonitor(
                metrics,
                event -> {
                    System.out.printf(
                            "[ALERT] rule=%s state=%s severity=%s message=%s%n",
                            event.ruleId(),
                            event.state(),
                            event.severity(),
                            event.message()
                    );

                    if (event.state() == ReliabilityMonitor.State.FIRING) {
                        if (activeAlertIncidents.containsKey(event.ruleId())) {
                            return;
                        }

                        String incidentId = "alert-" + UUID.randomUUID();
                        Incident incident = new Incident(
                                incidentId,
                                new Service("S001", "S001"),
                                "[" + event.ruleId() + "] " + event.message(),
                                event.severity()
                        );

                        try {
                            repository.save(incident);
                        } catch (SQLException exception) {
                            throw new IllegalStateException(
                                    "Failed to persist alert incident "
                                            + incidentId,
                                    exception
                            );
                        }

                        manager.reportIncident(incident);
                        activeAlertIncidents.put(
                                event.ruleId(), incidentId);
                    } else {
                        String incidentId =
                                activeAlertIncidents.remove(event.ruleId());
                        System.out.printf(
                                "[ALERT] recovery observed rule=%s incident=%s; "
                                        + "incident status is unchanged because "
                                        + "the incident model has no resolution lifecycle%n",
                                event.ruleId(),
                                incidentId == null ? "none" : incidentId
                        );
                    }
                },
                20,
                0.05,
                0.20
        );

        String adminUsername =
                System.getenv("NEXUS_ADMIN_USERNAME");
        String adminPassword =
                System.getenv("NEXUS_ADMIN_PASSWORD");
        String viewerUsername =
                System.getenv("NEXUS_VIEWER_USERNAME");
        String viewerPassword =
                System.getenv("NEXUS_VIEWER_PASSWORD");

        if (adminUsername == null || adminPassword == null
                || viewerUsername == null || viewerPassword == null) {
            throw new IllegalStateException(
                    "Configure all four NEXUS_* authentication environment variables.");
        }

        AuthService authService = new AuthService(
                adminUsername,
                adminPassword,
                viewerUsername,
                viewerPassword);

        ConcurrentIncidentProcessor processor =
                new ConcurrentIncidentProcessor(
                        2, 100,
                        incident -> System.out.println(
                                "Analyzing incident: "
                                        + incident.getId()));

        String redisUri = requiredEnv("REDIS_URI");

RedisIncidentCache incidentCache =
        new RedisIncidentCache(redisUri, telemetry);

RedisRateLimiter rateLimiter =
        new RedisRateLimiter(redisUri, 60, 60);

        int port = 8080;
        NexusApiServer server;

        try {
            // Verify connectivity before accepting requests.
            repository.count();

            server = new NexusApiServer(
                    port,
                    manager,
                    graph,
                    auditLogger,
                    processor,
                    repository,
                    authService,
                    incidentCache,
                    rateLimiter,
                    metrics,
                    telemetry
                );

            server.start();

        } catch (Exception exception) {
            processor.close();
            telemetry.close();
            throw new IOException(
                    "Failed to start NexusAI API", exception);
        }

        ScheduledExecutorService reliabilityScheduler =
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(
                            runnable, "nexusai-reliability-monitor");
                    thread.setDaemon(true);
                    return thread;
                });

        reliabilityScheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        monitor.evaluate();
                    } catch (RuntimeException exception) {
                        System.err.println(
                                "Reliability evaluation failed: "
                                        + exception.getMessage()
                        );
                    }
                },
                15,
                15,
                TimeUnit.SECONDS
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            reliabilityScheduler.shutdown();
            try {
                if (!reliabilityScheduler.awaitTermination(
                        5, TimeUnit.SECONDS)) {
                    reliabilityScheduler.shutdownNow();
                }
            } catch (InterruptedException exception) {
                reliabilityScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
            server.close();
            processor.close();
            rateLimiter.close();
            incidentCache.close();
            telemetry.close();
        }));

        System.out.println(
                "NexusAI API running at http://localhost:8080");
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