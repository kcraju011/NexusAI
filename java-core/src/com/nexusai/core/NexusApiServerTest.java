package com.nexusai.core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

public class NexusApiServerTest {

    private static void check(
            boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static HttpResponse<String> request(
            HttpClient client,
            String baseUrl,
            String method,
            String path,
            String body) throws Exception {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path));

        if (body != null) {
            builder.header("Content-Type", "application/json")
                    .method(method,
                            HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method,
                    HttpRequest.BodyPublishers.noBody());
        }

        return client.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString());
    }

    public static void main(String[] args) throws Exception {

        PostgresIncidentRepository repository =
                new PostgresIncidentRepository(
                        requiredEnv("NEXUS_DB_URL"),
                        requiredEnv("NEXUS_DB_USER"),
                        requiredEnv("NEXUS_DB_PASSWORD"));

        IncidentManager manager = new IncidentManager();
        ServiceGraph graph = new ServiceGraph();

        graph.addService("S001");
        graph.addService("S002");
        graph.addDependency("S001", "S002");

        String incidentId = "T" + UUID.randomUUID()
                .toString().replace("-", "");
        String body =
                "{\"id\":\"" + incidentId + "\","
                + "\"serviceId\":\"S001\","
                + "\"description\":\"Test incident\","
                + "\"severity\":\"HIGH\"}";

        try (ConcurrentIncidentProcessor processor =
                     new ConcurrentIncidentProcessor(
                             1, 10, incident -> { });
             NexusApiServer server =
                     new NexusApiServer(
                             0, manager, graph, processor, repository)) {

            server.start();

            String baseUrl =
                    "http://127.0.0.1:" + server.getPort();

            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> health = request(
                    client, baseUrl, "GET",
                    "/api/health", null);

            check(health.statusCode() == 200,
                    "Health should return 200");

            HttpResponse<String> created = request(
                    client, baseUrl, "POST",
                    "/api/incidents", body);

            check(created.statusCode() == 201,
                    "Creation should return 201");

            check(created.body().contains(
                            "\"id\":\"" + incidentId + "\""),
                    "Response should contain incident ID");

            check(created.body().contains("\"serviceId\":\"S001\""),
                    "Response should contain service ID");

            HttpResponse<String> fetched = request(
                    client, baseUrl, "GET",
                    "/api/incidents/" + incidentId, null);

            check(fetched.statusCode() == 200,
                    "Retrieval should return 200");

            check(fetched.body().contains("\"serviceId\":\"S001\""),
                    "Retrieved incident should contain service ID");

            HttpResponse<String> duplicate = request(
                    client, baseUrl, "POST",
                    "/api/incidents", body);

            check(duplicate.statusCode() == 409,
                    "Duplicate ID should return 409");

            HttpResponse<String> invalid = request(
                    client, baseUrl, "POST",
                    "/api/incidents",
                    "{\"id\":\"T2\","
                    + "\"serviceId\":\"S001\","
                    + "\"description\":\"Bad severity\","
                    + "\"severity\":\"URGENT\"}");

            check(invalid.statusCode() == 400,
                    "Invalid severity should return 400");

            HttpResponse<String> invalidService = request(
                    client, baseUrl, "POST",
                    "/api/incidents",
                    "{\"id\":\"T3\","
                    + "\"serviceId\":\"S999\","
                    + "\"description\":\"Unknown service\","
                    + "\"severity\":\"HIGH\"}");

            check(invalidService.statusCode() == 404,
                    "Unknown service should return 404");

            HttpResponse<String> missing = request(
                    client, baseUrl, "GET",
                    "/api/incidents/MISSING", null);

            check(missing.statusCode() == 404,
                    "Missing incident should return 404");

            HttpResponse<String> downstream = request(
                    client, baseUrl, "GET",
                    "/api/services/S001/downstream", null);

            check(downstream.statusCode() == 200
                            && downstream.body().contains("S002"),
                    "Downstream endpoint should return S002");

            HttpResponse<String> analysis = request(
                    client, baseUrl, "POST",
                    "/api/incidents/" + incidentId + "/analyze", "{}");

            check(analysis.statusCode() == 202,
                    "Analysis should return 202");

            System.out.println(
                    "All NexusAI HTTP API tests passed!");
        } finally {
            repository.deleteById(incidentId);
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