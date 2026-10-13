package com.nexusai.core;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;

public class IncidentManager {

    private final PriorityQueue<Incident> incidents =
            new PriorityQueue<>();

    private final Map<String, Incident> incidentsById =
            new HashMap<>();

    public synchronized void reportIncident(Incident incident) {
        if (incident == null) {
            throw new IllegalArgumentException(
                    "Incident cannot be null"
            );
        }

        if (incidentsById.containsKey(incident.getId())) {
            throw new IllegalArgumentException(
                    "Duplicate incident ID: " + incident.getId()
            );
        }

        incidents.offer(incident);
        incidentsById.put(incident.getId(), incident);
    }

    public synchronized Incident findIncidentById(String id) {
        return incidentsById.get(id);
    }

    public synchronized Incident getNextIncident() {
        Incident incident = incidents.poll();

        if (incident != null) {
            incidentsById.remove(incident.getId());
        }

        return incident;
    }

    public synchronized boolean hasIncidents() {
        return !incidents.isEmpty();
    }

    public synchronized int getIncidentCount() {
        return incidentsById.size();
    }
}