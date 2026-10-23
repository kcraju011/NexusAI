package com.nexusai.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetricsRegistryTest {

    @Test
    void recordsRequestsAndErrors() {
        MetricsRegistry metrics = new MetricsRegistry();

        metrics.recordRequest(200, 50_000_000);
        metrics.recordRequest(500, 200_000_000);

        String output = metrics.toPrometheusFormat();

        assertTrue(output.contains(
                "nexusai_http_requests_total 2"));
        assertTrue(output.contains(
                "nexusai_http_server_errors_total 1"));
        assertTrue(output.contains(
                "nexusai_http_responses_total{status=\"200\"} 1"));
        assertTrue(output.contains(
                "nexusai_http_responses_total{status=\"500\"} 1"));
    }

    @Test
    void recordsCacheAndRateLimitMetrics() {
        MetricsRegistry metrics = new MetricsRegistry();

        metrics.recordCacheHit();
        metrics.recordCacheHit();
        metrics.recordCacheMiss();
        metrics.recordRateLimitRejection();

        String output = metrics.toPrometheusFormat();

        assertTrue(output.contains(
                "nexusai_redis_cache_hits_total 2"));
        assertTrue(output.contains(
                "nexusai_redis_cache_misses_total 1"));
        assertTrue(output.contains(
                "nexusai_rate_limit_rejections_total 1"));
    }

    @Test
    void recordsLatencyInCorrectBuckets() {
        MetricsRegistry metrics = new MetricsRegistry();

        metrics.recordRequest(200, 10_000_000);  // 10 ms
        metrics.recordRequest(200, 200_000_000); // 200 ms

        String output = metrics.toPrometheusFormat();

        assertTrue(output.contains(
                "nexusai_http_request_duration_seconds_bucket{le=\"0.01\"} 1"));
        assertTrue(output.contains(
                "nexusai_http_request_duration_seconds_bucket{le=\"0.25\"} 2"));
        assertTrue(output.contains(
                "nexusai_http_request_duration_seconds_count 2"));
    }
}
