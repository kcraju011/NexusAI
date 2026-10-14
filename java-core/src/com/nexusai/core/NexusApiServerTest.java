package com.nexusai.core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                        String body,
                        String token) throws Exception {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path));

                if (token != null) {
                        builder.header("Authorization", "Bearer " + token);
                }

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
        AuthService authService = new AuthService(
                "test-admin", "test-admin-password",
                "test-viewer", "test-viewer-password");

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
                             0, manager, graph, processor,
                             repository, authService)) {

            server.start();

            String baseUrl =
                    "http://127.0.0.1:" + server.getPort();

            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> health = request(
                    client, baseUrl, "GET",
                    "/api/health", null, null);

            check(health.statusCode() == 200,
                    "Health should return 200");

            HttpResponse<String> unauthenticated = request(
                    client, baseUrl, "GET",
                    "/api/incidents", null, null);

            check(unauthenticated.statusCode() == 401,
                    "Incident listing should require authentication");

            HttpResponse<String> login = request(
                    client, baseUrl, "POST",
                    "/api/auth/login",
                    "{\"username\":\"test-admin\","
                            + "\"password\":\"test-admin-password\"}",
                    null);

            check(login.statusCode() == 200,
                    "Admin login should return 200");

            Matcher tokenMatcher = Pattern.compile(
                    "\"token\":\"([^\"]+)\"")
                    .matcher(login.body());
            check(tokenMatcher.find(), "Login response should include a token");
            String adminToken = tokenMatcher.group(1);

            HttpResponse<String> created = request(
                    client, baseUrl, "POST",
                    "/api/incidents", body, adminToken);

            check(created.statusCode() == 201,
                    "Creation should return 201");

            check(created.body().contains(
                            "\"id\":\"" + incidentId + "\""),
                    "Response should contain incident ID");

            check(created.body().contains("\"serviceId\":\"S001\""),
                    "Response should contain service ID");

            HttpResponse<String> fetched = request(
                    client, baseUrl, "GET",
                    "/api/incidents/" + incidentId, null, adminToken);

            check(fetched.statusCode() == 200,
                    "Retrieval should return 200");

            check(fetched.body().contains("\"serviceId\":\"S001\""),
                    "Retrieved incident should contain service ID");

            HttpResponse<String> duplicate = request(
                    client, baseUrl, "POST",
                    "/api/incidents", body, adminToken);

            check(duplicate.statusCode() == 409,
                    "Duplicate ID should return 409");

            HttpResponse<String> invalid = request(
                    client, baseUrl, "POST",
                    "/api/incidents",
                    "{\"id\":\"T2\","
                    + "\"serviceId\":\"S001\","
                    + "\"description\":\"Bad severity\","
                    + "\"severity\":\"URGENT\"}",
                    adminToken);

            check(invalid.statusCode() == 400,
                    "Invalid severity should return 400");

            HttpResponse<String> invalidService = request(
                    client, baseUrl, "POST",
                    "/api/incidents",
                    "{\"id\":\"T3\","
                    + "\"serviceId\":\"S999\","
                    + "\"description\":\"Unknown service\","
                    + "\"severity\":\"HIGH\"}",
                    adminToken);

            check(invalidService.statusCode() == 404,
                    "Unknown service should return 404");

            HttpResponse<String> missing = request(
                    client, baseUrl, "GET",
                    "/api/incidents/MISSING", null, adminToken);

            check(missing.statusCode() == 404,
                    "Missing incident should return 404");

            HttpResponse<String> downstream = request(
                    client, baseUrl, "GET",
                    "/api/services/S001/downstream", null, adminToken);

            check(downstream.statusCode() == 200
                            && downstream.body().contains("S002"),
                    "Downstream endpoint should return S002");

            HttpResponse<String> analysis = request(
                    client, baseUrl, "POST",
                    "/api/incidents/" + incidentId + "/analyze", "{}",
                    adminToken);

            check(analysis.statusCode() == 202,
                    "Analysis should return 202");

            HttpResponse<String> viewerLogin = request(
                    client, baseUrl, "POST",
                    "/api/auth/login",
                    "{\"username\":\"test-viewer\","
                            + "\"password\":\"test-viewer-password\"}",
                    null);
            Matcher viewerTokenMatcher = Pattern.compile(
                    "\"token\":\"([^\"]+)\"")
                    .matcher(viewerLogin.body());

            check(viewerLogin.statusCode() == 200
                            && viewerTokenMatcher.find(),
                    "Viewer login should return a token");
            String viewerToken = viewerTokenMatcher.group(1);

            HttpResponse<String> viewerRead = request(
                    client, baseUrl, "GET",
                    "/api/incidents", null, viewerToken);

            check(viewerRead.statusCode() == 200,
                    "Viewer reads should be allowed");

            HttpResponse<String> viewerWrite = request(
                    client, baseUrl, "POST",
                    "/api/incidents", body, viewerToken);

            check(viewerWrite.statusCode() == 403,
                    "Viewer writes should be forbidden");

            HttpResponse<String> logout = request(
                    client, baseUrl, "POST",
                    "/api/auth/logout", null, viewerToken);

            check(logout.statusCode() == 200,
                    "Authenticated viewer should be able to log out");

            HttpResponse<String> afterLogout = request(
                    client, baseUrl, "GET",
                    "/api/incidents", null, viewerToken);

            check(afterLogout.statusCode() == 401,
                    "Logged-out tokens should no longer be accepted");

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