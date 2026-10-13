package com.nexusai.core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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

        IncidentManager manager = new IncidentManager();
        ServiceGraph graph = new ServiceGraph();

        graph.addService("S001");
        graph.addService("S002");
        graph.addDependency("S001", "S002");

        try (ConcurrentIncidentProcessor processor =
                     new ConcurrentIncidentProcessor(
                             1, 10, incident -> { })) {

            try (NexusApiServer server =
                         new NexusApiServer(
                                 0, manager, graph, processor)) {

                server.start();

                String baseUrl =
                        "http://127.0.0.1:" + server.getPort();

                HttpClient client = HttpClient.newHttpClient();

                HttpResponse<String> health = request(
                        client, baseUrl, "GET",
                        "/api/health", null);

                check(health.statusCode() == 200,
                        "Health should return 200");

                String body =
                        "{\"id\":\"T1\","
                        + "\"description\":\"Test incident\","
                        + "\"severity\":\"HIGH\"}";

                HttpResponse<String> created = request(
                        client, baseUrl, "POST",
                        "/api/incidents", body);

                check(created.statusCode() == 201,
                        "Creation should return 201");

                check(created.body().contains("\"id\":\"T1\""),
                        "Response should contain incident ID");

                HttpResponse<String> fetched = request(
                        client, baseUrl, "GET",
                        "/api/incidents/T1", null);

                check(fetched.statusCode() == 200,
                        "Retrieval should return 200");

                HttpResponse<String> duplicate = request(
                        client, baseUrl, "POST",
                        "/api/incidents", body);

                check(duplicate.statusCode() == 409,
                        "Duplicate ID should return 409");

                HttpResponse<String> invalid = request(
                        client, baseUrl, "POST",
                        "/api/incidents",
                        "{\"id\":\"T2\","
                        + "\"description\":\"Bad severity\","
                        + "\"severity\":\"URGENT\"}");

                check(invalid.statusCode() == 400,
                        "Invalid severity should return 400");

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
                        "/api/incidents/T1/analyze", "{}");

                check(analysis.statusCode() == 202,
                        "Analysis should return 202");

                System.out.println(
                        "All NexusAI HTTP API tests passed!");
            }
        }
    }
}