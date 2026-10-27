
package com.nexusai.core;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public final class RedisIncidentCache implements AutoCloseable {

    private static final String PREFIX = "nexusai:incident:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> redis;
    private final Telemetry telemetry;

    public RedisIncidentCache(String redisUri, Telemetry telemetry) {
        this.client = RedisClient.create(redisUri);
        this.connection = client.connect();
        this.redis = connection.sync();
        this.telemetry = telemetry;
    }

    public Optional<Incident> get(String id) {
        Span telemetrySpan =
                telemetry.startSpan("redis.incident.get");
        try {
            try {
                try (Tracing.Span span =
                     Tracing.startSpan("redis.incident.get")) {
                    try {
                        Map<String, String> fields =
                                redis.hgetall(key(id));

                        if (fields.isEmpty()) {
                            return Optional.empty();
                        }

                        try {
                            String description = fields.get("description");
                            String serviceId = fields.get("serviceId");
                            String severity = fields.get("severity");

                            if (description == null || serviceId == null
                                    || severity == null) {
                                telemetrySpan.setStatus(StatusCode.ERROR);
                                invalidate(id);
                                return Optional.empty();
                            }

                            // Refresh the TTL after a successful cache lookup.
                            redis.expire(key(id), TTL);

                            return Optional.of(new Incident(
                                    id,
                                    new Service(serviceId, serviceId),
                                    description,
                                    Incident.Severity.valueOf(severity)
                            ));
                        } catch (IllegalArgumentException ex) {
                            // Malformed cached data should not break incident retrieval.
                            telemetrySpan.setStatus(StatusCode.ERROR);
                            invalidate(id);
                            return Optional.empty();
                        }
                    } catch (RuntimeException exception) {
                        span.fail(exception);
                        throw exception;
                    }
                }
            } catch (RuntimeException exception) {
                Telemetry.recordError(telemetrySpan, exception);
                throw exception;
            }
        } finally {
            telemetrySpan.end();
        }
    }

    public void put(Incident incident) {
        String key = key(incident.getId());

        redis.hset(key, Map.of(
                "serviceId", incident.getService().getId(),
                "description", incident.getDescription(),
                "severity", incident.getSeverity().name()
        ));

        redis.expire(key, TTL);
    }

    public void invalidate(String id) {
        redis.del(key(id));
    }

    private String key(String id) {
        return PREFIX + id;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } finally {
            client.shutdown();
        }
    }
}
