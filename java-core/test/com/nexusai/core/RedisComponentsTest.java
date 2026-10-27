
package com.nexusai.core;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.api.StatefulRedisConnection;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RedisComponentsTest {

    private boolean hasConfiguredRedisUri() {
        String uri = System.getenv("REDIS_TEST_URI");
        return uri != null && !uri.isBlank();
    }

    private String redisUri() {
        String uri = System.getenv("REDIS_TEST_URI");
        return uri == null || uri.isBlank()
                ? "redis://localhost:6379"
                : uri;
    }

    private void requireRedis() {
        RedisClient client = RedisClient.create(redisUri());
        try {
            try (StatefulRedisConnection<String, String> connection =
                         client.connect()) {
                assertEquals("PONG", connection.sync().ping());
            }
        } catch (RedisConnectionException exception) {
            if (hasConfiguredRedisUri()) {
                throw exception;
            }
            Assumptions.assumeTrue(
                    false,
                    "Redis is not running at localhost:6379; "
                            + "set REDIS_TEST_URI to use another Redis instance"
            );
        } finally {
            client.shutdown();
        }
    }

    @Test
    void incidentCanBeCachedAndInvalidated() {
        requireRedis();
        String id = "TEST-" + java.util.UUID.randomUUID();

        try (Telemetry telemetry =
                     new Telemetry("http://localhost:4317");
             RedisIncidentCache cache =
                     new RedisIncidentCache(redisUri(), telemetry)) {

            Incident incident = new Incident(
                    id,
                    new Service("S001", "S001"),
                    "Redis cache integration test",
                    Incident.Severity.HIGH
            );

            cache.put(incident);

            var result = cache.get(id);

            assertTrue(result.isPresent());
            assertEquals(id, result.get().getId());
            assertEquals("S001", result.get().getService().getId());
            assertEquals(
                    Incident.Severity.HIGH,
                    result.get().getSeverity()
            );

            cache.invalidate(id);

            assertTrue(cache.get(id).isEmpty());
        }
    }

    @Test
    void rateLimiterAllowsRequestsUpToLimit() {
        requireRedis();
        try (RedisRateLimiter limiter =
                     new RedisRateLimiter(redisUri(), 3, 60)) {

            String client = "test-" + java.util.UUID.randomUUID();

            assertTrue(limiter.allow(client));
            assertTrue(limiter.allow(client));
            assertTrue(limiter.allow(client));
            assertFalse(limiter.allow(client));
        }
    }
}
