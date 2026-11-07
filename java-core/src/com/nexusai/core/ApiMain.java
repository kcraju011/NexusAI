package com.nexusai.core;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
        IncidentCorrelationService correlationService =
                new IncidentCorrelationService();
        IncidentDeduplicationService deduplicationService =
                new IncidentDeduplicationService();
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
        PostgresIncidentLifecycleRepository lifecycleRepository =
                new PostgresIncidentLifecycleRepository(
                        url, username, password);
        IncidentRecoveryService recoveryService =
                new IncidentRecoveryService(
                        lifecycleRepository,
                        3,
                        Duration.ofSeconds(30)
                );

        MetricsRegistry metrics = new MetricsRegistry();

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
                    correlationService,
                    deduplicationService,
                    lifecycleRepository,
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

                    IncidentDeduplicationService.Failure failure =
                            new IncidentDeduplicationService.Failure(
                                    "nexusai-api",
                                    "reliability." + event.ruleId(),
                                    "ReliabilityAlert",
                                    event.ruleId());
                    try {
                        if (event.state()
                                == ReliabilityMonitor.State.FIRING) {
                            server.recordFailure(
                                    failure,
                                    event.severity(),
                                    "[" + event.ruleId() + "] "
                                            + event.message());
                        } else if (!server.resolveFailure(failure)) {
                            AppLogger.warn(
                                    "alert_incident_resolution_skipped",
                                    "No active incident could be resolved for "
                                            + event.ruleId());
                        }
                    } catch (SQLException exception) {
                        throw new IllegalStateException(
                                "Failed to persist reliability incident "
                                        + event.ruleId(),
                                exception
                        );
                    }
                },
                20,
                0.05,
                0.20
        );

        ScheduledExecutorService reliabilityScheduler =
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(
                            runnable, "nexusai-reliability-monitor");
                    thread.setDaemon(true);
                    return thread;
                });

        IncidentDeduplicationService.Failure databaseHealthFailure =
                new IncidentDeduplicationService.Failure(
                        "nexusai-api",
                        "postgres.health_check",
                        "SQLException",
                        "DATABASE_HEALTH_CHECK_FAILED"
                );
        AtomicReference<String> databaseIncidentId =
                new AtomicReference<>();

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
                    checkDatabaseHealth(
                            repository,
                            server,
                            recoveryService,
                            databaseHealthFailure,
                            databaseIncidentId
                    );
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

    private static void checkDatabaseHealth(
            PostgresIncidentRepository repository,
            NexusApiServer server,
            IncidentRecoveryService recoveryService,
            IncidentDeduplicationService.Failure failure,
            AtomicReference<String> incidentId
    ) {
        try {
            repository.count();
        } catch (SQLException healthCheckFailure) {
            try {
                IncidentDeduplicationService.Detection detection =
                        server.recordFailure(
                                failure,
                                Incident.Severity.HIGH,
                                "Database health check failed: "
                                        + healthCheckFailure.getMessage()
                        );
                incidentId.set(detection.incidentId());
                recoveryService.recordFailure(
                        detection.incidentId(),
                        Instant.now()
                );
            } catch (SQLException incidentFailure) {
                if (healthCheckFailure != incidentFailure) {
                    incidentFailure.addSuppressed(healthCheckFailure);
                }
                AppLogger.error(
                        "database_health_incident_record_failed",
                        "Database health check failed and its incident "
                                + "could not be recorded",
                        incidentFailure
                );
            }
            return;
        }

        String activeIncidentId = incidentId.get();
        if (activeIncidentId == null) {
            return;
        }

        IncidentRecoveryService.RecoveryResult recovery =
                recoveryService.recordSuccess(
                        activeIncidentId,
                        Instant.now()
                );
        if (recovery.status()
                == IncidentRecoveryService.RecoveryStatus.RESOLVED) {
            try {
                if (server.resolveFailure(failure)) {
                    incidentId.compareAndSet(activeIncidentId, null);
                } else {
                    AppLogger.warn(
                            "database_health_incident_resolution_pending",
                            "Recovery confirmed but the incident record "
                                    + "could not be marked resolved"
                    );
                }
            } catch (SQLException resolutionFailure) {
                AppLogger.error(
                        "database_health_incident_resolution_failed",
                        "Recovery confirmed but incident persistence failed",
                        resolutionFailure
                );
            }
        }
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