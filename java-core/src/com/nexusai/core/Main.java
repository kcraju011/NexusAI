package com.nexusai.core;

import static com.nexusai.core.Incident.Severity.*;

public class Main {

    public static void main(String[] args) {
        IncidentManager manager = new IncidentManager();

        Service gateway = new Service("S001", "API Gateway");
        Service payment = new Service("S002", "Payment Service");
        Service database = new Service("S003", "Database");
        Service notification = new Service("S004", "Notification Service");

        manager.reportIncident(new Incident(
                "I001", payment, "Payment is slow", MEDIUM));

        manager.reportIncident(new Incident(
                "I002", gateway, "Gateway is unavailable", CRITICAL));

        manager.reportIncident(new Incident(
                "I003", notification, "Notification errors increased", HIGH));

        System.out.println("=== NexusAI Incident Engine ===");
        System.out.println("Active incidents: "
                + manager.getIncidentCount());

        System.out.println("Lookup I003: "
                + manager.findIncidentById("I003"));

        System.out.println("\nProcessing incidents:");

        while (manager.hasIncidents()) {
            System.out.println(manager.getNextIncident());
        }

        System.out.println("Active incidents after processing: "
                + manager.getIncidentCount());

        ServiceGraph graph = new ServiceGraph();

        graph.addService(gateway.getId());
        graph.addService(payment.getId());
        graph.addService(database.getId());
        graph.addService(notification.getId());

        graph.addDependency(gateway.getId(), payment.getId());
        graph.addDependency(gateway.getId(), notification.getId());
        graph.addDependency(payment.getId(), database.getId());

        System.out.println("\nDownstream of API Gateway: "
                + graph.findDownstreamServices(gateway.getId()));

                LRUCache<String, Double> metricsCache =
        new LRUCache<>(3);

metricsCache.put("payment.cpu", 78.5);
metricsCache.put("payment.memory", 82.1);
metricsCache.put("gateway.latency", 145.0);

System.out.println("\n=== Metrics Cache ===");
System.out.println(
        "Payment CPU: " + metricsCache.get("payment.cpu")
);

metricsCache.put("database.cpu", 91.2);

System.out.println(
        "Gateway latency cached: "
        + metricsCache.containsKey("gateway.latency")
);

System.out.println(
        "Payment memory cached: "
        + metricsCache.containsKey("payment.memory")
);
    }
}