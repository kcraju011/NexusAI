package com.nexusai.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.nexusai.core.Incident.Severity;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class NexusApiServer implements AutoCloseable {
    

    private final PostgresIncidentRepository repository;
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private static final Pattern JSON_FIELD = Pattern.compile(
            "\"([A-Za-z]+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
    );

    private final HttpServer server;
    private final ExecutorService httpExecutor;
    private final AuditLogger auditLogger;
    private final IncidentManager incidentManager;
    private final ServiceGraph serviceGraph;
    private final ConcurrentIncidentProcessor processor;
    private final AuthService authService;

    public NexusApiServer(
       
     int port,
        IncidentManager incidentManager,
        ServiceGraph serviceGraph, AuditLogger auditLogger,
        ConcurrentIncidentProcessor processor,
        PostgresIncidentRepository repository,
        AuthService authService)
        throws IOException {

    this.incidentManager = incidentManager;
    this.serviceGraph = serviceGraph;
    this.auditLogger = auditLogger;
    this.processor = processor;
    this.repository = repository;
    this.authService = authService;
        server = HttpServer.create(
                new InetSocketAddress("0.0.0.0", port), 0);

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

        String requestId = RequestContext.start(
            exchange.getRequestHeaders().getFirst("X-Request-ID")
        );
        exchange.getResponseHeaders().set("X-Request-ID", requestId);
        long requestStartedAt = System.nanoTime();
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        AppLogger.info(
            "request_started",
            method + " " + path
        );

        try {
            Headers headers = exchange.getResponseHeaders();

            headers.set(
                "Access-Control-Allow-Origin",
                "http://localhost:5173");

            headers.set(
                "Access-Control-Allow-Methods",
                "GET, POST, OPTIONS");

            headers.set(
                "Access-Control-Allow-Headers",
                "Content-Type, Authorization, X-Request-ID");

            headers.set("Vary", "Origin");
            headers.set(
    "Access-Control-Allow-Headers",
    "Content-Type, Authorization, X-Request-ID"
);

            if ("OPTIONS".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
            }

                boolean publicHealth =
                    "GET".equals(method) && "/api/health".equals(path);

                boolean loginRequest =
                    "POST".equals(method) && "/api/auth/login".equals(path);

                if (publicHealth) {
                route(exchange, null);
                return;
                }

                if (loginRequest) {
                handleLogin(exchange);
                return;
                }

                String token = bearerToken(exchange);
                AuthService.AuthenticatedUser user =
                    authService.authenticate(token);

                if (user == null) {
                AppLogger.warn(
                        "authentication_rejected",
                        "Authentication required or session expired"
                );
                ApiErrors.send(
                        exchange,
                        401,
                        "UNAUTHORIZED",
                        "Authentication required or session expired"
                );
                return;
                }

                if ("POST".equals(method) && "/api/auth/logout".equals(path)) {
                handleLogout(exchange, token);
                return;
                }

                if (user.role() != AuthService.Role.ADMIN
                    && !"GET".equals(method)) {
                auditLogger.record(
                        user.username(),
                        "AUTHORIZATION_DENIED",
                        path,
                        "DENIED"
                );
                ApiErrors.send(
                        exchange,
                        403,
                        "FORBIDDEN",
                        "Insufficient permissions"
                );
                return;
                }

            route(exchange, user);
        } catch (ApiException exception) {
            AppLogger.warn(
                    "request_rejected",
                    "API request rejected with code " + exception.getCode()
            );

            if (exchange.getResponseCode() == -1) {
                ApiErrors.send(
                        exchange,
                        exception.getStatus(),
                        exception.getCode(),
                        exception.getMessage()
                );
            }
        } catch (Exception exception) {
            AppLogger.error(
                    "request_failed",
                    "Unexpected API error",
                    exception
            );

            if (exchange.getResponseCode() == -1) {
                ApiErrors.send(
                        exchange,
                        500,
                        "INTERNAL_ERROR",
                        "An unexpected server error occurred"
                );
            }
        } finally {
            long durationMillis = java.util.concurrent.TimeUnit.NANOSECONDS
                .toMillis(System.nanoTime() - requestStartedAt);
            AppLogger.info(
                "request_completed",
                method + " " + path
                    + " status=" + exchange.getResponseCode()
                    + " durationMs=" + durationMillis
            );
            RequestContext.clear();
            exchange.close();
        }

    }

        private void route(
            HttpExchange exchange,
            AuthService.AuthenticatedUser user
        )
            throws IOException, SQLException {

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if ("GET".equals(method) && "/api/health".equals(path)) {
            sendJson(exchange, 200,
                    "{\"status\":\"UP\",\"service\":\"NexusAI\"}");
            return;
        }

        if ("POST".equals(method)
                && "/api/incidents".equals(path)) {
            createIncident(exchange, user);
            return;
        }

        if ("GET".equals(method)
                && "/api/incidents".equals(path)) {
            listIncidents(exchange);
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
                analyzeIncident(exchange, id, user);
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

  private void createIncident(
      HttpExchange exchange,
      AuthService.AuthenticatedUser user
  )
        throws IOException, SQLException {

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
                severityText.toUpperCase(
                        java.util.Locale.ROOT));
    } catch (IllegalArgumentException exception) {
        throw new ApiException(
            400,
            "INVALID_REQUEST",
            "Invalid incident severity"
        );
    }

    Incident incident = new Incident(
            id, new Service(serviceId, serviceId),
            description, severity);

    // Persist first. PostgreSQL enforces unique IDs.
    try {
        repository.save(incident);
    } catch (SQLException exception) {
        if ("23505".equals(exception.getSQLState())) {
            sendJson(exchange, 409,
                    error("Incident ID already exists"));
            return;
        }

        throw exception;
    }

    // Update the in-memory scheduling and lookup structures.
    incidentManager.reportIncident(incident);

        auditLogger.record(
            user.username(),
            "INCIDENT_CREATE",
            id,
            "SUCCESS"
        );

    sendJson(exchange, 201, incidentJson(incident));
}

    private void listIncidents(HttpExchange exchange)
            throws IOException, SQLException {

        List<Incident> incidents = repository.findAll();
        List<String> values = new ArrayList<>();

        for (Incident incident : incidents) {
            values.add(incidentJson(incident));
        }

        sendJson(exchange, 200,
                "[" + String.join(",", values) + "]");
    }

 private void getIncident(
        HttpExchange exchange, String id)
        throws IOException, SQLException {

    Incident incident =
            incidentManager.findIncidentById(id);

    if (incident == null) {
        incident = repository.findById(id).orElse(null);
    }

    if (incident == null) {
        sendJson(exchange, 404,
                error("Incident not found"));
        return;
    }

    sendJson(exchange, 200, incidentJson(incident));
}

    private void analyzeIncident(
            HttpExchange exchange,
            String id,
            AuthService.AuthenticatedUser user
        )
            throws IOException, SQLException {

        Incident incident =
                incidentManager.findIncidentById(id);

        if (incident == null) {
            incident = repository.findById(id).orElse(null);
        }

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

            auditLogger.record(
                user.username(),
                "INCIDENT_ANALYZE",
                id,
                "SUCCESS"
            );

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
            throw new ApiException(
                413,
                "REQUEST_TOO_LARGE",
                "Request body exceeds 64 KB"
            );
        }

        return new String(bytes, StandardCharsets.UTF_8);
    }

    private Map<String, String> parseJson(String body) {
        String trimmed = body.trim();

        if (!trimmed.startsWith("{")
                || !trimmed.endsWith("}")) {
            throw new ApiException(
                400,
                "INVALID_REQUEST",
                "Expected a JSON object"
            );
        }

        Map<String, String> fields = new HashMap<>();
        Matcher matcher = JSON_FIELD.matcher(trimmed);

        while (matcher.find()) {
            fields.put(
                    matcher.group(1),
                    unescape(matcher.group(2)));
        }

        if (fields.isEmpty()) {
            throw new ApiException(
                400,
                "INVALID_REQUEST",
                "JSON object has no recognized string fields"
            );
        }

        return fields;
    }

    private String required(
            Map<String, String> fields, String key) {

        String value = fields.get(key);

        if (value == null || value.isBlank()) {
            throw new ApiException(
                400,
                "INVALID_REQUEST",
                "Missing required field: " + key
            );
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
            + "\"serviceId\":"
            + quote(incident.getService().getId()) + ","
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


private String bearerToken(HttpExchange exchange) {
    String header = exchange.getRequestHeaders()
            .getFirst("Authorization");

    if (header == null || !header.startsWith("Bearer ")) {
        return null;
    }

    String token = header.substring("Bearer ".length()).trim();
    return token.isEmpty() ? null : token;
}

private void handleLogin(HttpExchange exchange) throws IOException {
    try {
        String body = readBody(exchange);
        Map<String, String> data = parseJson(body);

        String username = required(data, "username");
        String password = required(data, "password");

        String token = authService.login(username, password);

        if (token == null) {
            auditLogger.record(
                username,
                "LOGIN",
                "AUTH",
                "FAILURE"
            );
            ApiErrors.send(
                exchange,
                401,
                "UNAUTHORIZED",
                "Invalid username or password"
            );
            return;
        }

        auditLogger.record(
            username,
            "LOGIN",
            "AUTH",
            "SUCCESS"
        );

        sendJson(exchange, 200,
                "{\"token\":" + quote(token)
                        + ",\"tokenType\":\"Bearer\""
                        + ",\"expiresIn\":1800}");

    } catch (IllegalArgumentException exception) {
        sendJson(exchange, 400,
                "{\"error\":\"Invalid login request\"}");
    }
}

private void handleLogout(
        HttpExchange exchange,
        String token
) throws IOException {
    authService.logout(token);
    sendJson(exchange, 200, "{\"message\":\"Logged out\"}");
}

}