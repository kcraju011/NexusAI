package com.nexusai.core;

import io.opentelemetry.context.Context;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ConcurrentIncidentProcessor
        implements AutoCloseable {

    private final ThreadPoolExecutor executor;
    private final Consumer<Incident> handler;

    public ConcurrentIncidentProcessor(
            int workerCount,
            int queueCapacity,
            Consumer<Incident> handler) {

        if (workerCount <= 0) {
            throw new IllegalArgumentException(
                    "Worker count must be positive");
        }

        if (queueCapacity <= 0) {
            throw new IllegalArgumentException(
                    "Queue capacity must be positive");
        }

        this.handler = Objects.requireNonNull(
                handler, "Handler cannot be null");

        this.executor = new ThreadPoolExecutor(
                workerCount,
                workerCount,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public void submit(Incident incident) {
        Objects.requireNonNull(
                incident, "Incident cannot be null");

        String traceId = Tracing.currentTraceId();
        Context otelContext = Context.current();

        executor.execute(() -> {
            String previousTraceId = Tracing.currentTraceId();

            try (var scope = otelContext.makeCurrent()) {
                Tracing.endRequest();

                if ("no-active-trace".equals(traceId)) {
                    handler.accept(incident);
                    return;
                }

                Tracing.beginRequest(traceId);
                try (Tracing.Span span =
                             Tracing.startSpan("incident.process")) {
                    try {
                        handler.accept(incident);
                    } catch (RuntimeException exception) {
                        span.fail(exception);
                        throw exception;
                    }
                }
            } finally {
                Tracing.endRequest();

                if (!"no-active-trace".equals(previousTraceId)) {
                    Tracing.beginRequest(previousTraceId);
                }
            }
        });
    }

    public int getPendingTaskCount() {
        return executor.getQueue().size();
    }

    public int getActiveWorkerCount() {
        return executor.getActiveCount();
    }

    public long getCompletedTaskCount() {
        return executor.getCompletedTaskCount();
    }

    public void shutdownGracefully() {
        executor.shutdown();

        try {
            if (!executor.awaitTermination(
                    10, TimeUnit.SECONDS)) {

                executor.shutdownNow();

                if (!executor.awaitTermination(
                        5, TimeUnit.SECONDS)) {
                    System.err.println(
                            "Some tasks may not have terminated");
                }
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        shutdownGracefully();
    }
}