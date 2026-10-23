
package com.nexusai.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public final class MetricsRegistry {

    public record Snapshot(
            long totalRequests,
            long serverErrors,
            long requestsUnder250ms
    ) {}

    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder serverErrors = new LongAdder();

    private final LongAdder cacheHits = new LongAdder();
    private final LongAdder cacheMisses = new LongAdder();
    private final LongAdder rateLimitRejections = new LongAdder();

    private final LongAdder totalRequestNanos = new LongAdder();

    private final Map<Integer, LongAdder> statusCounts =
            new ConcurrentHashMap<>();

    // Upper bounds in seconds. The final +Inf bucket is added when exporting.
    private final double[] buckets = {
            0.005, 0.01, 0.025, 0.05, 0.1,
            0.25, 0.5, 1.0, 2.5, 5.0
    };

    private final LongAdder[] latencyBuckets =
            new LongAdder[buckets.length];

    public MetricsRegistry() {
        for (int i = 0; i < latencyBuckets.length; i++) {
            latencyBuckets[i] = new LongAdder();
        }
    }

    public void recordRequest(int statusCode, long elapsedNanos) {
        totalRequests.increment();
        totalRequestNanos.add(Math.max(0, elapsedNanos));

        statusCounts.computeIfAbsent(
                statusCode, ignored -> new LongAdder()
        ).increment();

        if (statusCode >= 500) {
            serverErrors.increment();
        }

        double seconds = Math.max(0, elapsedNanos) / 1_000_000_000.0;

        for (int i = 0; i < buckets.length; i++) {
            if (seconds <= buckets[i]) {
                latencyBuckets[i].increment();
            }
        }
    }

    public void recordCacheHit() {
        cacheHits.increment();
    }

    public void recordCacheMiss() {
        cacheMisses.increment();
    }

    public void recordRateLimitRejection() {
        rateLimitRejections.increment();
    }

    public Snapshot snapshot() {
        return new Snapshot(
                totalRequests.sum(),
                serverErrors.sum(),
                latencyBuckets[5].sum()
        );
    }

    public String toPrometheusFormat() {
        StringBuilder out = new StringBuilder();

        out.append("# HELP nexusai_http_requests_total Total HTTP requests\n");
        out.append("# TYPE nexusai_http_requests_total counter\n");
        out.append("nexusai_http_requests_total ")
                .append(totalRequests.sum()).append('\n');

        out.append("# HELP nexusai_http_server_errors_total HTTP 5xx responses\n");
        out.append("# TYPE nexusai_http_server_errors_total counter\n");
        out.append("nexusai_http_server_errors_total ")
                .append(serverErrors.sum()).append('\n');

        out.append("# HELP nexusai_http_responses_total Responses by status code\n");
        out.append("# TYPE nexusai_http_responses_total counter\n");

        statusCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> out.append(
                        "nexusai_http_responses_total{status=\"")
                        .append(entry.getKey()).append("\"} ")
                        .append(entry.getValue().sum()).append('\n'));

        out.append("# HELP nexusai_http_request_duration_seconds Request duration\n");
        out.append("# TYPE nexusai_http_request_duration_seconds histogram\n");

        for (int i = 0; i < buckets.length; i++) {
            out.append("nexusai_http_request_duration_seconds_bucket{le=\"")
                    .append(buckets[i]).append("\"} ")
                    .append(latencyBuckets[i].sum()).append('\n');
        }

        out.append("nexusai_http_request_duration_seconds_bucket{le=\"+Inf\"} ")
                .append(totalRequests.sum()).append('\n');

        out.append("nexusai_http_request_duration_seconds_count ")
                .append(totalRequests.sum()).append('\n');

        out.append("nexusai_http_request_duration_seconds_sum ")
                .append(totalRequestNanos.sum() / 1_000_000_000.0)
                .append('\n');

        out.append("# HELP nexusai_redis_cache_hits_total Redis cache hits\n");
        out.append("# TYPE nexusai_redis_cache_hits_total counter\n");
        out.append("nexusai_redis_cache_hits_total ")
                .append(cacheHits.sum()).append('\n');

        out.append("# HELP nexusai_redis_cache_misses_total Redis cache misses\n");
        out.append("# TYPE nexusai_redis_cache_misses_total counter\n");
        out.append("nexusai_redis_cache_misses_total ")
                .append(cacheMisses.sum()).append('\n');

        out.append("# HELP nexusai_rate_limit_rejections_total Rate-limited requests\n");
        out.append("# TYPE nexusai_rate_limit_rejections_total counter\n");
        out.append("nexusai_rate_limit_rejections_total ")
                .append(rateLimitRejections.sum()).append('\n');

        return out.toString();
    }

}
