package com.nexusai.core;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Objects;

public class Incident implements Comparable<Incident> {

    public enum Severity {
        CRITICAL(1),
        HIGH(2),
        MEDIUM(3),
        LOW(4);

        private final int rank;

        Severity(int rank) {
            this.rank = rank;
        }
    }

    private final String id;
    private final Service service;
    private final String description;
    private final Severity severity;
    private final LocalDateTime createdAt;
    private final String fingerprint;
    private final Instant firstSeen;
    private long occurrenceCount;
    private Instant lastSeen;
    private Instant resolvedAt;

    public Incident(
            String id,
            Service service,
            String description,
            Severity severity) {
        this(id, service, description, severity, null, 1,
                Instant.now(), Instant.now(), null);
    }

    public Incident(
            String id,
            Service service,
            String description,
            Severity severity,
            String fingerprint,
            long occurrenceCount,
            Instant firstSeen,
            Instant lastSeen,
            Instant resolvedAt) {
        if (occurrenceCount < 1) {
            throw new IllegalArgumentException(
                    "occurrenceCount must be at least 1");
        }
        this.id = id;
        this.service = service;
        this.description = description;
        this.severity = severity;
        this.createdAt = LocalDateTime.now();
        this.fingerprint = fingerprint;
        this.occurrenceCount = occurrenceCount;
        this.firstSeen = Objects.requireNonNull(firstSeen, "firstSeen");
        this.lastSeen = Objects.requireNonNull(lastSeen, "lastSeen");
        this.resolvedAt = resolvedAt;
    }

    public String getId() {
        return id;
    }

    public Service getService() {
        return service;
    }

    public Severity getSeverity() {
        return severity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    public String getDescription() {
        return description;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public Instant getFirstSeen() {
        return firstSeen;
    }

    public synchronized long getOccurrenceCount() {
        return occurrenceCount;
    }

    public synchronized Instant getLastSeen() {
        return lastSeen;
    }

    public synchronized Instant getResolvedAt() {
        return resolvedAt;
    }

    public synchronized void recordOccurrence(
            long occurrenceCount, Instant lastSeen) {
        if (occurrenceCount < this.occurrenceCount) {
            throw new IllegalArgumentException(
                    "occurrenceCount cannot decrease");
        }
        this.occurrenceCount = occurrenceCount;
        this.lastSeen = Objects.requireNonNull(lastSeen, "lastSeen");
    }

    public synchronized void resolve(Instant resolvedAt) {
        this.resolvedAt = Objects.requireNonNull(resolvedAt, "resolvedAt");
    }

    @Override
    public int compareTo(Incident other) {
        int result = Integer.compare(
                this.severity.rank,
                other.severity.rank
        );

        if (result == 0) {
            return this.createdAt.compareTo(other.createdAt);
        }

        return result;
    }

    @Override
    public String toString() {
        return id + " | " + severity + " | " + service.getName() + " | " + description;
    }
}