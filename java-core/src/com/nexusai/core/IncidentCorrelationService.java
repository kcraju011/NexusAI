
package com.nexusai.core;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class IncidentCorrelationService {

    private static final Pattern TRACE_ID_PATTERN =
            Pattern.compile("^[a-fA-F0-9]{32}$");

    private final Map<String, Correlation> correlations =
            new ConcurrentHashMap<>();

    public record Correlation(
            String incidentId,
            String traceId,
            String serviceName,
            String operationName,
            String errorType,
            String message,
            Instant linkedAt
    ) {}

    public Correlation link(
            String incidentId,
            String traceId,
            String serviceName,
            String operationName,
            String errorType,
            String message
    ) {
        requireText(incidentId, "incidentId");
        requireText(serviceName, "serviceName");
        requireText(operationName, "operationName");
        requireText(errorType, "errorType");
        requireText(message, "message");

        if (traceId == null ||
                !TRACE_ID_PATTERN.matcher(traceId).matches()) {
            throw new IllegalArgumentException("Invalid trace ID");
        }

        Correlation correlation = new Correlation(
                incidentId,
                traceId.toLowerCase(),
                serviceName,
                operationName,
                errorType,
                message,
                Instant.now()
        );

        correlations.put(incidentId, correlation);
        return correlation;
    }

    public Optional<Correlation> findByIncidentId(String incidentId) {
        return Optional.ofNullable(correlations.get(incidentId));
    }

    public boolean remove(String incidentId) {
        return correlations.remove(incidentId) != null;
    }

    public int count() {
        return correlations.size();
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be empty"
            );
        }
    }
}
