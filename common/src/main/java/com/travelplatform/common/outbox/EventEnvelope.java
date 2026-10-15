package com.travelplatform.common.outbox;

import java.time.Instant;

/**
 * Wire envelope shared by every Kafka topic (contracts/events.md). Consumers
 * deduplicate on {@code id}, and {@code traceId} keeps the async hop inside the
 * same end-to-end trace as the originating HTTP call (宪法 VIII).
 */
public record EventEnvelope(
        String id,
        String type,
        String aggregateId,
        Instant occurredAt,
        String traceId,
        Object payload) {
}
