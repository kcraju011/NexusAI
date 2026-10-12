package com.nexusai.core;

import java.time.LocalDateTime;

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

    public Incident(
            String id,
            Service service,
            String description,
            Severity severity) {
        this.id = id;
        this.service = service;
        this.description = description;
        this.severity = severity;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public Severity getSeverity() {
        return severity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
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