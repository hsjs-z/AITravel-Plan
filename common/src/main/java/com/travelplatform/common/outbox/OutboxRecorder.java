package com.travelplatform.common.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.common.observability.TraceContext;

import java.time.Instant;
import java.util.UUID;

/**
 * Producer-side entry point: builds the event envelope, serializes it once and hands
 * the row to the service's outbox store. Business code never touches Kafka directly,
 * so the event is persisted atomically with the business change (宪法 IV).
 */
public class OutboxRecorder {

    private final OutboxStore store;
    private final ObjectMapper objectMapper;

    public OutboxRecorder(OutboxStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    public void record(String topic, String eventType, String aggregateType,
                       String aggregateId, Object payload) {
        Instant occurredAt = Instant.now();
        String traceId = TraceContext.current() != null ? TraceContext.current() : UUID.randomUUID().toString();
        EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID().toString(), eventType, aggregateId, occurredAt, traceId, payload);

        OutboxMessage message = new OutboxMessage();
        message.setId(envelope.id());
        message.setTopic(topic);
        message.setEventType(eventType);
        message.setAggregateType(aggregateType);
        message.setAggregateId(aggregateId);
        message.setTraceId(traceId);
        message.setCreatedAt(occurredAt);
        message.setPayload(serialize(envelope));
        store.persist(message);
    }

    private String serialize(EventEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize event " + envelope.type(), e);
        }
    }
}
