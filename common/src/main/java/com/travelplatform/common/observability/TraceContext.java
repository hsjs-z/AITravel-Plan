package com.travelplatform.common.observability;

import org.slf4j.MDC;

/**
 * Lightweight trace context holder. Keeps the trace id in the logging MDC so
 * every log line carries the same correlation id; full propagation (HTTP and
 * Kafka headers) is wired at the OpenTelemetry instrumentation layer.
 */
public final class TraceContext {

    public static final String KEY = "traceId";

    private TraceContext() {
    }

    public static void put(String traceId) {
        MDC.put(KEY, traceId);
    }

    public static String current() {
        return MDC.get(KEY);
    }

    public static void clear() {
        MDC.remove(KEY);
    }
}
