package com.nexusai.core;

import static com.nexusai.core.Incident.Severity.*;
import java.util.Arrays;

public class EngineTest {

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        testPriorityAndLookup();
        testDuplicateIncident();
        testServiceGraph();

        System.out.println("All Day 2 tests passed!");
    }

    private static void testPriorityAndLookup() {
        IncidentManager manager = new IncidentManager();
        Service service = new Service("S001", "Test Service");

        Incident medium = new Incident("I1", service, "Slow", MEDIUM);
        Incident critical = new Incident("I2", service, "Down", CRITICAL);

        manager.reportIncident(medium);
        manager.reportIncident(critical);

        check(manager.getIncidentCount() == 2,
                "Incident count should be 2");

        check(manager.findIncidentById("I1") == medium,
                "Lookup should return the correct incident");

        check(manager.getNextIncident() == critical,
                "Critical incident should be processed first");

        check(manager.findIncidentById("I2") == null,
                "Processed incident should be removed from lookup");

        check(manager.getIncidentCount() == 1,
                "One incident should remain");
    }

    private static void testDuplicateIncident() {
        IncidentManager manager = new IncidentManager();
        Service service = new Service("S001", "Test Service");

        manager.reportIncident(
            new Incident("I1", service, "First", HIGH));

        boolean rejected = false;

        try {
            manager.reportIncident(
                    new Incident("I1", service, "Duplicate", CRITICAL));
        } catch (IllegalArgumentException exception) {
            rejected = true;
        }

        check(rejected, "Duplicate IDs must be rejected");
        check(manager.getIncidentCount() == 1,
                "Duplicate must not alter incident count");
    }

    private static void testServiceGraph() {
        ServiceGraph graph = new ServiceGraph();

        graph.addService("A");
        graph.addService("B");
        graph.addService("C");

        graph.addDependency("A", "B");
        graph.addDependency("B", "C");

        check(graph.findDownstreamServices("A")
                        .equals(Arrays.asList("B", "C")),
                "BFS should find downstream services in order");

        System.out.println("Service graph test passed");
    }
}