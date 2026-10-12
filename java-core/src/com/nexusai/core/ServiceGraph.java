package com.nexusai.core;

import java.util.*;

public class ServiceGraph {

    private final Map<String, List<String>> graph =
            new HashMap<>();

    public void addService(String serviceId) {
        graph.putIfAbsent(serviceId, new ArrayList<>());
    }

    public void addDependency(
            String upstreamId,
            String downstreamId) {

        if (!graph.containsKey(upstreamId)
                || !graph.containsKey(downstreamId)) {
            throw new IllegalArgumentException(
                    "Both services must exist"
            );
        }

        if (upstreamId.equals(downstreamId)) {
            throw new IllegalArgumentException(
                    "A service cannot depend on itself"
            );
        }

        List<String> dependencies = graph.get(upstreamId);

        if (!dependencies.contains(downstreamId)) {
            dependencies.add(downstreamId);
        }
    }

    public List<String> findDownstreamServices(
            String startingServiceId) {

        if (!graph.containsKey(startingServiceId)) {
            throw new IllegalArgumentException(
                    "Unknown service: " + startingServiceId
            );
        }

        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();

        queue.offer(startingServiceId);
        visited.add(startingServiceId);

        while (!queue.isEmpty()) {
            String current = queue.poll();

            for (String neighbor : graph.get(current)) {
                if (visited.add(neighbor)) {
                    queue.offer(neighbor);
                }
            }
        }

        visited.remove(startingServiceId);

        return new ArrayList<>(visited);
    }
}