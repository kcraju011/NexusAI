
package com.nexusai.core;

import java.util.UUID;

import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

public final class RedisRateLimiter implements AutoCloseable {

    private static final String SCRIPT = """
        local count = redis.call('INCR', KEYS[1])
        if count == 1 then
            redis.call('EXPIRE', KEYS[1], ARGV[1])
        end
        return count
        """;

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisCommands<String, String> redis;
    private final int limit;
    private final int windowSeconds;

    public RedisRateLimiter(
            String redisUri,
            int limit,
            int windowSeconds) {

        if (limit < 1 || windowSeconds < 1) {
            throw new IllegalArgumentException(
                    "Limit and window must be positive"
            );
        }

        this.client = RedisClient.create(redisUri);
        this.connection = client.connect();
        this.redis = connection.sync();
        this.limit = limit;
        this.windowSeconds = windowSeconds;
    }

    public boolean allow(String clientId) {
        String key = "nexusai:rate:"
                + UUID.nameUUIDFromBytes(
                        clientId.getBytes(
                                java.nio.charset.StandardCharsets.UTF_8
                        )
                );

        Long count = redis.eval(
                SCRIPT,
                ScriptOutputType.INTEGER,
                new String[]{key},
                Integer.toString(windowSeconds)
        );

        return count != null && count <= limit;
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
