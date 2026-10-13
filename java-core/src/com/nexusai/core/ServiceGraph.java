package com.nexusai.core;

import java.util.*;

public class ServiceGraph {

    // Insertion order makes demonstrations predictable.
    private final Map<String, List<String>> graph =
            new LinkedHashMap<>();

    public void addService(String serviceId) {
        validateId(serviceId);
        graph.putIfAbsent(serviceId, new ArrayList<>());
    }

    public boolean hasService(String serviceId) {
        return graph.containsKey(serviceId);
    }

    public void addDependency(
            String upstreamId,
            String downstreamId) {

        validateId(upstreamId);
        validateId(downstreamId);

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

    private void validateId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(
                    "Service ID cannot be blank"
            );
        }
    }

    // BFS: finds all services reachable from a starting service.
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

    // Kahn's algorithm: returns upstream-before-downstream order.
    // Throws an exception if the graph contains a cycle.
    public List<String> topologicalSort() {

        Map<String, Integer> indegree = new LinkedHashMap<>();

        for (String serviceId : graph.keySet()) {
            indegree.put(serviceId, 0);
        }

        for (List<String> dependencies : graph.values()) {
            for (String dependency : dependencies) {
                indegree.put(
                        dependency,
                        indegree.get(dependency) + 1
                );
            }
        }

        Queue<String> ready = new ArrayDeque<>();

        for (String serviceId : graph.keySet()) {
            if (indegree.get(serviceId) == 0) {
                ready.offer(serviceId);
            }
        }

        List<String> order = new ArrayList<>();

        while (!ready.isEmpty()) {
            String current = ready.poll();
            order.add(current);

            for (String neighbor : graph.get(current)) {
                int newIndegree = indegree.get(neighbor) - 1;
                indegree.put(neighbor, newIndegree);

                if (newIndegree == 0) {
                    ready.offer(neighbor);
                }
            }
        }

        if (order.size() != graph.size()) {
            throw new IllegalStateException(
                    "Cannot sort services: dependency cycle detected"
            );
        }

        return order;
    }

    public boolean hasCycle() {
        try {
            topologicalSort();
            return false;
        } catch (IllegalStateException exception) {
            return true;
        }
    }

    public int getServiceCount() {
        return graph.size();
    }
}