package com.nexusai.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.nexusai.core.Incident.Severity.HIGH;

public class ConcurrentIncidentProcessorTest {
        private static final Service TEST_SERVICE =
                        new Service("TEST", "Test Service");

    private static void check(
            boolean condition, String message) {

        if (!condition) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args)
            throws InterruptedException {

        testConcurrentProcessing();
        testBoundedQueue();
        testGracefulShutdown();

        System.out.println(
                "All Day 4 concurrency tests passed!");
    }

    private static void testConcurrentProcessing()
            throws InterruptedException {

        int total = 100;

        AtomicInteger processed = new AtomicInteger();
        Set<String> ids = ConcurrentHashMap.newKeySet();
        CountDownLatch completed = new CountDownLatch(total);

        try (ConcurrentIncidentProcessor processor =
                     new ConcurrentIncidentProcessor(
                             4, 200, incident -> {
                                 ids.add(incident.getId());
                                 processed.incrementAndGet();
                                 completed.countDown();
                             })) {

            for (int i = 0; i < total; i++) {
                processor.submit(new Incident(
                        "I" + i,
                        TEST_SERVICE,
                        "Simulated incident " + i,
                        HIGH));
            }

            check(completed.await(10, TimeUnit.SECONDS),
                    "Not all incidents finished in time");

            check(processed.get() == total,
                    "Expected 100 processed incidents");

            check(ids.size() == total,
                    "Every incident ID should be unique");
        }

        System.out.println(
                "Concurrent processing test passed");
    }

    private static void testBoundedQueue()
            throws InterruptedException {

        CountDownLatch firstTaskStarted =
                new CountDownLatch(1);

        CountDownLatch releaseFirstTask =
                new CountDownLatch(1);

        try (ConcurrentIncidentProcessor processor =
                     new ConcurrentIncidentProcessor(
                             1, 1, incident -> {
                                 firstTaskStarted.countDown();

                                 try {
                                     releaseFirstTask.await();
                                 } catch (InterruptedException e) {
                                     Thread.currentThread().interrupt();
                                 }
                             })) {

            processor.submit(new Incident(
                    "B1", TEST_SERVICE, "Running task", HIGH));

            check(firstTaskStarted.await(
                            5, TimeUnit.SECONDS),
                    "First task did not start");

            processor.submit(new Incident(
                    "B2", TEST_SERVICE, "Queued task", HIGH));

            boolean rejected = false;

            try {
                processor.submit(new Incident(
                        "B3", TEST_SERVICE, "Overflow task", HIGH));
            } catch (RejectedExecutionException exception) {
                rejected = true;
            }

            check(rejected,
                    "Saturated processor should reject a task");

            releaseFirstTask.countDown();
        }

        System.out.println("Bounded queue test passed");
    }

    private static void testGracefulShutdown()
            throws InterruptedException {

        CountDownLatch completed = new CountDownLatch(5);

        ConcurrentIncidentProcessor processor =
                new ConcurrentIncidentProcessor(
                        2, 10, incident -> completed.countDown());

        for (int i = 0; i < 5; i++) {
            processor.submit(new Incident(
                    "S" + i, TEST_SERVICE, "Shutdown test", HIGH));
        }

        processor.shutdownGracefully();

        check(completed.getCount() == 0,
                "Accepted tasks should finish on shutdown");

        boolean rejected = false;

        try {
            processor.submit(new Incident(
                    "S6", TEST_SERVICE, "After shutdown", HIGH));
        } catch (RejectedExecutionException exception) {
            rejected = true;
        }

        check(rejected,
                "New tasks must be rejected after shutdown");

        System.out.println("Graceful shutdown test passed");
    }
}