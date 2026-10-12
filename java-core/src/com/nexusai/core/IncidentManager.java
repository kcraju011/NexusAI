package com.nexusai.core;

import java.util.Comparator;
import java.util.PriorityQueue;

public class IncidentManager {

    private final PriorityQueue<Incident> incidents =
            new PriorityQueue<>(Comparator
                .comparing(Incident::getSeverity)
                .thenComparing(Incident::getCreatedAt));

    public void reportIncident(Incident incident) {
        incidents.offer(incident);
    }

    public Incident getNextIncident() {
        return incidents.poll();
    }

    public boolean hasIncidents() {
        return !incidents.isEmpty();
    }

    public int getIncidentCount() {
        return incidents.size();
    }
}