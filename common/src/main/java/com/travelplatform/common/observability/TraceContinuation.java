package com.travelplatform.common.observability;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.propagation.Propagator;

import java.security.SecureRandom;
import java.util.Map;

/**
 * Bridges a stored 32-hex trace id (the single source of truth persisted in the
 * outbox row) back into the OpenTelemetry trace: the producer writes a W3C
 * {@code traceparent} header, and the async hop (outbox relay thread, Kafka
 * consumer, AI planning executor) rebuilds a child span under that same trace.
 */
public final class TraceContinuation {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final String TRACEPARENT = "traceparent";

    private TraceContinuation() {
    }

    /** Builds a valid W3C {@code traceparent} for a 32-hex trace id, or {@code null}. */
    public static String traceparentFor(String traceId) {
        if (traceId == null || traceId.length() != 32 || !traceId.matches("[0-9a-fA-F]{32}")) {
            return null;
        }
        return "00-" + traceId.toLowerCase() + "-" + randomHex(8) + "-01";
    }

    /** Starts a consumer-side span whose parent is the remote {@code traceparent}. */
    public static Span startFromRemote(Propagator propagator, String traceparent, String name) {
        Map<String, String> carrier = Map.of(TRACEPARENT, traceparent);
        return propagator.extract(carrier, (map, key) -> map.get(key))
                .name(name)
                .kind(Span.Kind.CONSUMER)
                .start();
    }

    private static String randomHex(int bytes) {
        byte[] random = new byte[bytes];
        RANDOM.nextBytes(random);
        StringBuilder builder = new StringBuilder(bytes * 2);
        for (byte value : random) {
            builder.append(HEX[(value >> 4) & 0xF]).append(HEX[value & 0xF]);
        }
        return builder.toString();
    }
}
