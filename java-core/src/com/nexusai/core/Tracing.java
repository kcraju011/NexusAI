
package com.nexusai.core;

import java.util.UUID;
import java.util.logging.Logger;

public final class Tracing {

    private static final Logger LOGGER =
            Logger.getLogger(Tracing.class.getName());

    private static final ThreadLocal<String> CURRENT_TRACE =
            new ThreadLocal<>();

    private Tracing() {}

    public static String beginRequest(String incomingTraceId) {
        // Accept only a 32-character hexadecimal trace ID.
        // Invalid external values are replaced with a new ID.
        String traceId;

        if (incomingTraceId != null
                && incomingTraceId.matches("[a-fA-F0-9]{32}")) {
            traceId = incomingTraceId.toLowerCase();
        } else {
            traceId = UUID.randomUUID()
                    .toString()
                    .replace("-", "");
        }

        CURRENT_TRACE.set(traceId);
        return traceId;
    }

    public static String currentTraceId() {
        String traceId = CURRENT_TRACE.get();

        if (traceId == null) {
            return "no-active-trace";
        }

        return traceId;
    }

    public static Span startSpan(String operation) {
        return new Span(operation, System.nanoTime(), currentTraceId());
    }

    public static void endRequest() {
        CURRENT_TRACE.remove();
    }

    public static final class Span implements AutoCloseable {
        private final String operation;
        private final long startedNanos;
        private final String traceId;

        private String outcome = "OK";
        private boolean closed;

        private Span(String operation, long startedNanos, String traceId) {
            this.operation = operation;
            this.startedNanos = startedNanos;
            this.traceId = traceId;
        }

        public void fail(Throwable error) {
            outcome = "ERROR";

            // Log the exception type only, not arbitrary exception messages.
            LOGGER.warning(() -> "trace_id=" + traceId
                    + " span=" + operation
                    + " outcome=ERROR"
                    + " exception_type="
                    + error.getClass().getSimpleName());
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }

            closed = true;
            long durationNanos = System.nanoTime() - startedNanos;
            double durationMs = durationNanos / 1_000_000.0;

            LOGGER.info(() -> "trace_id=" + traceId
                    + " span=" + operation
                    + " outcome=" + outcome
                    + " duration_ms="
                    + String.format(java.util.Locale.ROOT, "%.3f", durationMs));
        }
    }
}
