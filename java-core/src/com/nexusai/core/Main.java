package com.nexusai.core;

import static com.nexusai.core.Incident.Severity.*;

public class Main {

    public static void main(String[] args) {
        IncidentManager manager = new IncidentManager();

        Service payment = new Service(
                "S001", "Payment Service"
        );

        manager.reportIncident(new Incident(
                "I001",
                payment,
                payment.getName() + " is responding slowly",
                MEDIUM
        ));

        manager.reportIncident(new Incident(
                "I002",
                payment,
                payment.getName() + " is unavailable",
                CRITICAL
        ));

        manager.reportIncident(new Incident(
                "I003",
                payment,
                payment.getName() + " has elevated errors",
                HIGH
        ));

        System.out.println("NexusAI Incident Manager");

        while (manager.hasIncidents()) {
            System.out.println(manager.getNextIncident());
        }
    }
}