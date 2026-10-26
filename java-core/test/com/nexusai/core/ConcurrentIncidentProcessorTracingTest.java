package com.nexusai.core;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentIncidentProcessorTracingTest {

    @Test
    void propagatesTraceToWorkerAndClearsItBetweenTasks()
            throws InterruptedException {
        AtomicReference<String> propagatedTraceId =
                new AtomicReference<>();
        AtomicReference<String> untracedTaskContext =
                new AtomicReference<>();
        CountDownLatch tracedTaskCompleted = new CountDownLatch(1);
        CountDownLatch untracedTaskCompleted = new CountDownLatch(1);

        try (ConcurrentIncidentProcessor processor =
                     new ConcurrentIncidentProcessor(
                             1,
                             2,
                             incident -> {
                                 if ("traced".equals(incident.getId())) {
                                     propagatedTraceId.set(
                                             Tracing.currentTraceId());
                                     tracedTaskCompleted.countDown();
                                 } else {
                                     untracedTaskContext.set(
                                             Tracing.currentTraceId());
                                     untracedTaskCompleted.countDown();
                                 }
                             })) {
            String traceId =
                    "0123456789abcdef0123456789abcdef";
            Tracing.beginRequest(traceId);
            try {
                processor.submit(incident("traced"));
            } finally {
                Tracing.endRequest();
            }

            assertTrue(tracedTaskCompleted.await(2, TimeUnit.SECONDS));
            assertEquals(traceId, propagatedTraceId.get());

            processor.submit(incident("untraced"));

            assertTrue(untracedTaskCompleted.await(2, TimeUnit.SECONDS));
            assertEquals("no-active-trace", untracedTaskContext.get());
        }
    }

    private static Incident incident(String id) {
        return new Incident(
                id,
                new Service("S001", "S001"),
                "Processor tracing test",
                Incident.Severity.HIGH
        );
    }
}
