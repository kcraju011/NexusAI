package com.nexusai.core;

import com.nexusai.core.Incident.Severity;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NexusApiServer implements AutoCloseable {

    private static final int MAX_BODY_BYTES = 64 * 1024;

    private static final Pattern JSON_FIELD = Pattern.compile(
            "\"([A-Za-z]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
    );

    private final HttpServer server;
    private final ExecutorService httpExecutor;

    private final IncidentManager incidentManager;
    private final ServiceGraph serviceGraph;
    private final ConcurrentIncidentProcessor processor;

    public NexusApiServer(
            int port,
            IncidentManager incidentManager,
            ServiceGraph serviceGraph,
            ConcurrentIncidentProcessor processor)
            throws IOException {

        this.incidentManager = incidentManager;
        this.serviceGraph = serviceGraph;
        this.processor = processor;

        server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", port), 0);

        httpExecutor = Executors.newFixedThreadPool(4);
        server.setExecutor(httpExecutor);
        server.createContext("/", this::handleRequest);
    }

    public void start() {
        server.start();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    private void handleRequest(HttpExchange exchange)
            throws IOException {

        try {
            route(exchange);
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400, error(exception.getMessage()));
        } catch (Exception exception) {
            exception.printStackTrace();

            sendJson(exchange, 500,
                    error("Internal server error"));
        } finally {
            exchange.close();
        }
    }

    private void route(HttpExchange exchange)
            throws IOException {

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if ("GET".equals(method) && "/api/health".equals(path)) {
            sendJson(exchange, 200,
                    "{\"status\":\"UP\",\"service\":\"NexusAI\"}");
            return;
        }

        if ("POST".equals(method)
                && "/api/incidents".equals(path)) {
            createIncident(exchange);
            return;
        }

        if ("GET".equals(method)
                && path.startsWith("/api/incidents/")) {

            String id = path.substring(
                    "/api/incidents/".length());

            if (!id.isBlank() && !id.contains("/")) {
                getIncident(exchange, id);
                return;
            }
        }

        if ("POST".equals(method)
                && path.startsWith("/api/incidents/")
                && path.endsWith("/analyze")) {

            String id = path.substring(
                    "/api/incidents/".length(),
                    path.length() - "/analyze".length());

            if (!id.isBlank() && !id.contains("/")) {
                analyzeIncident(exchange, id);
                return;
            }
        }

        if ("GET".equals(method)
                && path.startsWith("/api/services/")
                && path.endsWith("/downstream")) {

            String id = path.substring(
                    "/api/services/".length(),
                    path.length() - "/downstream".length());

            if (!id.isBlank() && !id.contains("/")) {
                getDownstreamServices(exchange, id);
                return;
            }
        }

        if (path.startsWith("/api/")) {
            sendJson(exchange, 404,
                    error("Endpoint not found"));
        } else {
            sendJson(exchange, 404,
                    error("Resource not found"));
        }
    }

    private void createIncident(HttpExchange exchange)
            throws IOException {

        String body = readBody(exchange);
        Map<String, String> fields = parseJson(body);

        String id = required(fields, "id");
        String serviceId = required(fields, "serviceId");
        String description = required(fields, "description");
        String severityText = required(fields, "severity");

        if (!serviceGraph.hasService(serviceId)) {
            sendJson(exchange, 404,
                error("Service not found"));
            return;
        }

        final Severity severity;

        try {
            severity = Severity.valueOf(
                    severityText.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 400,
                    error("Severity must be CRITICAL, HIGH, MEDIUM or LOW"));
            return;
        }

        Incident incident = new Incident(
            id,
            new Service(serviceId, serviceId),
            description,
            severity);

        try {
            incidentManager.reportIncident(incident);
        } catch (IllegalArgumentException exception) {
            if (incidentManager.findIncidentById(id) != null) {
                sendJson(exchange, 409,
                        error("Incident ID already exists"));
            } else {
                sendJson(exchange, 400,
                        error(exception.getMessage()));
            }
            return;
        }

        sendJson(exchange, 201, incidentJson(incident));
    }

    private void getIncident(
            HttpExchange exchange, String id)
            throws IOException {

        Incident incident =
                incidentManager.findIncidentById(id);

        if (incident == null) {
            sendJson(exchange, 404,
                    error("Incident not found"));
            return;
        }

        sendJson(exchange, 200, incidentJson(incident));
    }

    private void analyzeIncident(
            HttpExchange exchange, String id)
            throws IOException {

        Incident incident =
                incidentManager.findIncidentById(id);

        if (incident == null) {
            sendJson(exchange, 404,
                    error("Incident not found"));
            return;
        }

        try {
            processor.submit(incident);
        } catch (RejectedExecutionException exception) {
            sendJson(exchange, 503,
                    error("Analysis queue is full or shutting down"));
            return;
        }

        sendJson(exchange, 202,
                "{\"status\":\"ACCEPTED\",\"incidentId\":"
                        + quote(id) + "}");
    }

    private void getDownstreamServices(
            HttpExchange exchange, String id)
            throws IOException {

        final List<String> downstream;

        try {
            downstream =
                    serviceGraph.findDownstreamServices(id);
        } catch (IllegalArgumentException exception) {
            sendJson(exchange, 404,
                    error("Service not found"));
            return;
        }

        List<String> values = new ArrayList<>();

        for (String serviceId : downstream) {
            values.add(quote(serviceId));
        }

        sendJson(exchange, 200,
                "{\"serviceId\":" + quote(id)
                        + ",\"downstream\":"
                        + "[" + String.join(",", values) + "]"
                        + "}");
    }

    private String readBody(HttpExchange exchange)
            throws IOException {

        byte[] bytes;

        try (InputStream input = exchange.getRequestBody()) {
            bytes = input.readNBytes(MAX_BODY_BYTES + 1);
        }

        if (bytes.length > MAX_BODY_BYTES) {
            throw new IllegalArgumentException(
                    "Request body exceeds 64 KB");
        }

        return new String(bytes, StandardCharsets.UTF_8);
    }

    private Map<String, String> parseJson(String body) {
        String trimmed = body.trim();

        if (!trimmed.startsWith("{")
                || !trimmed.endsWith("}")) {
            throw new IllegalArgumentException(
                    "Expected a JSON object");
        }

        Map<String, String> fields = new HashMap<>();
        Matcher matcher = JSON_FIELD.matcher(trimmed);

        while (matcher.find()) {
            fields.put(
                    matcher.group(1),
                    unescape(matcher.group(2)));
        }

        if (fields.isEmpty()) {
            throw new IllegalArgumentException(
                    "JSON object has no recognized string fields");
        }

        return fields;
    }

    private String required(
            Map<String, String> fields, String key) {

        String value = fields.get(key);

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing required field: " + key);
        }

        return value;
    }

    private String unescape(String value) {
        return value
                .replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\\", "\\");
    }

    private String incidentJson(Incident incident) {
        return "{"
                + "\"id\":" + quote(incident.getId()) + ","
                + "\"description\":"
                + quote(incident.getDescription()) + ","
                + "\"severity\":"
                + quote(incident.getSeverity().name())
                + "}";
    }

    private String error(String message) {
        return "{\"error\":" + quote(message) + "}";
    }
private void sendJson(
        HttpExchange exchange,
        int status,
        String body) throws IOException {

    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

    exchange.getResponseHeaders().set(
            "Content-Type", "application/json; charset=UTF-8");

    exchange.getResponseHeaders().set(
            "Cache-Control", "no-store");

    exchange.sendResponseHeaders(status, bytes.length);

    exchange.getResponseBody().write(bytes);
}


    private String quote(String value) {
        StringBuilder result = new StringBuilder("\"");

        for (char ch : value.toCharArray()) {
            switch (ch) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        result.append(String.format(
                                "\\u%04x", (int) ch));
                    } else {
                        result.append(ch);
                    }
                }
            }
        }

        return result.append('"').toString();
    }

    @Override
    public void close() {
        server.stop(1);
        httpExecutor.shutdown();
    }
}