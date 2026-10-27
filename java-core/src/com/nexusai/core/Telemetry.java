
package com.nexusai.core;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;

import java.time.Duration;

public final class Telemetry implements AutoCloseable {

    private final SdkTracerProvider tracerProvider;
    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;

    public Telemetry(String otlpEndpoint) {
        Resource resource = Resource.getDefault().merge(
                Resource.create(Attributes.of(
                        AttributeKey.stringKey("service.name"),
                        "nexusai-api"
                ))
        );

        OtlpGrpcSpanExporter exporter =
                OtlpGrpcSpanExporter.builder()
                        .setEndpoint(otlpEndpoint)
                        .setTimeout(Duration.ofSeconds(5))
                        .build();

        SpanProcessor processor =
                BatchSpanProcessor.builder(exporter)
                        .setScheduleDelay(Duration.ofSeconds(1))
                        .build();

        tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(processor)
                .build();

        openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(
                        W3CTraceContextPropagator.getInstance()
                ))
                .build();

        tracer = openTelemetry.getTracer("com.nexusai");
    }

    public Tracer tracer() {
        return tracer;
    }

    public OpenTelemetry openTelemetry() {
        return openTelemetry;
    }

    public Span startSpan(String operationName) {
        return tracer.spanBuilder(operationName)
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
    }

    public static void recordError(Span span, Throwable error) {
        span.recordException(error);
        span.setStatus(StatusCode.ERROR);
    }

    @Override
    public void close() {
        tracerProvider.close();
    }
}
