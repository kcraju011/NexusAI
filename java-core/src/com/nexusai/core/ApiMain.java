package com.nexusai.core;

import java.io.IOException;

public class ApiMain {

    public static void main(String[] args) throws IOException {

        String url = requiredEnv("NEXUS_DB_URL");
        String username = requiredEnv("NEXUS_DB_USER");
        String password = requiredEnv("NEXUS_DB_PASSWORD");

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
                        url, username, password);

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
        new RedisIncidentCache(redisUri);

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
                    rateLimiter
                );

            server.start();

        } catch (Exception exception) {
            processor.close();
            throw new IOException(
                    "Failed to start NexusAI API", exception);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            processor.close();
            rateLimiter.close();
            incidentCache.close();
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