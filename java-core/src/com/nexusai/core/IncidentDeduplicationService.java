
package com.nexusai.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class IncidentDeduplicationService {

    public record Failure(
            String serviceName,
            String operationName,
            String errorType,
            String errorCode
    ) {}

    public record Detection(
            String incidentId,
            String fingerprint,
            boolean newIncident,
            long occurrenceCount,
            Instant firstSeen,
            Instant lastSeen
    ) {}

    private static final class ActiveFailure {
        private final String incidentId;
        private final Instant firstSeen;
        private long occurrenceCount;
        private Instant lastSeen;

        private ActiveFailure(String incidentId, Instant now) {
            this.incidentId = incidentId;
            this.firstSeen = now;
            this.lastSeen = now;
            this.occurrenceCount = 1;
        }

        private Detection record(String fingerprint, Instant now,
                                 boolean newIncident) {
            if (!newIncident) {
                occurrenceCount++;
            }

            lastSeen = now;

            return new Detection(
                    incidentId,
                    fingerprint,
                    newIncident,
                    occurrenceCount,
                    firstSeen,
                    lastSeen
            );
        }
    }

    private final ConcurrentHashMap<String, ActiveFailure> activeFailures =
            new ConcurrentHashMap<>();

    public Detection detect(Failure failure) {
        validate(failure);

        String fingerprint = createFingerprint(failure);
        Instant now = Instant.now();

        AtomicReference<Detection> result = new AtomicReference<>();

        activeFailures.compute(fingerprint, (key, existing) -> {
            if (existing == null) {
                ActiveFailure created =
                        new ActiveFailure(
                                "INC-" + UUID.randomUUID(),
                                now
                        );

                result.set(created.record(key, now, true));
                return created;
            }

            result.set(existing.record(key, now, false));
            return existing;
        });

        return result.get();
    }

    public boolean resolve(String fingerprint) {
        if (fingerprint == null || fingerprint.isBlank()) {
            return false;
        }

        return activeFailures.remove(fingerprint) != null;
    }

    public int activeFailureCount() {
        return activeFailures.size();
    }

    public String createFingerprint(Failure failure) {
        validate(failure);

        String normalized = normalize(failure.serviceName()) + "|"
                + normalize(failure.operationName()) + "|"
                + normalize(failure.errorType()) + "|"
                + normalize(failure.errorCode());

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    normalized.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", e
            );
        }
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void validate(Failure failure) {
        if (failure == null) {
            throw new IllegalArgumentException("Failure is required");
        }

        requireText(failure.serviceName(), "serviceName");
        requireText(failure.operationName(), "operationName");
        requireText(failure.errorType(), "errorType");
        requireText(failure.errorCode(), "errorCode");
    }

    private void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    field + " must not be empty"
            );
        }
    }
}
