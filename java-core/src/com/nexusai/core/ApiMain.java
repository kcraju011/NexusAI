package com.nexusai.core;

import java.io.IOException;

public class ApiMain {

    public static void main(String[] args) throws IOException {

        IncidentManager incidentManager =
                new IncidentManager();

        ServiceGraph serviceGraph = new ServiceGraph();

        serviceGraph.addService("S001"); // API Gateway
        serviceGraph.addService("S002"); // Payment
        serviceGraph.addService("S003"); // Database
        serviceGraph.addService("S004"); // Notifications

        serviceGraph.addDependency("S001", "S002");
        serviceGraph.addDependency("S002", "S003");
        serviceGraph.addDependency("S001", "S004");

        ConcurrentIncidentProcessor processor =
                new ConcurrentIncidentProcessor(
                        2,
                        100,
                        incident -> System.out.println(
                                "Analyzing incident "
                                        + incident.getId()
                                        + " on "
                                        + Thread.currentThread().getName()
                        )
                );

        NexusApiServer server;

        try {
            server = new NexusApiServer(
                    8080,
                    incidentManager,
                    serviceGraph,
                    processor
            );

            server.start();

        } catch (IOException | RuntimeException exception) {
            processor.close();
            throw exception;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.close();
            processor.close();
        }));

        System.out.println(
                "NexusAI API running at http://localhost:8080");
        System.out.println("Press Ctrl+C to stop.");
    }
}