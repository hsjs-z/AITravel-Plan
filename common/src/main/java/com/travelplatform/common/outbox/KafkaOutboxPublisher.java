package com.travelplatform.common.outbox;

import com.travelplatform.common.observability.TraceContext;
import com.travelplatform.common.observability.TraceContinuation;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Publishes outbox rows to Kafka. The aggregate id is used as the message key so all
 * events of one aggregate keep their relative order, and the trace id is copied into
 * the record headers so the consumer can continue the same trace (宪法 VIII).
 */
public class KafkaOutboxPublisher implements OutboxPublisher {

    private static final long SEND_TIMEOUT_SECONDS = 5;

    public static final String HEADER_EVENT_ID = "eventId";
    public static final String HEADER_EVENT_TYPE = "eventType";

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public KafkaOutboxPublisher(KafkaTemplate<Object, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(OutboxMessage message) {
        ProducerRecord<Object, Object> record =
                new ProducerRecord<>(message.getTopic(), message.getAggregateId(), message.getPayload());
        record.headers().add(HEADER_EVENT_ID, bytes(message.getId()));
        record.headers().add(HEADER_EVENT_TYPE, bytes(message.getEventType()));
        if (message.getTraceId() != null) {
            record.headers().add(TraceContext.KEY, bytes(message.getTraceId()));
            String traceparent = TraceContinuation.traceparentFor(message.getTraceId());
            if (traceparent != null) {
                record.headers().add("traceparent", bytes(traceparent));
            }
        }
        try {
            kafkaTemplate.send(record).get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while publishing event " + message.getId(), e);
        } catch (Exception e) {
            throw new IllegalStateException("failed to publish event " + message.getId()
                    + " to " + message.getTopic() + ": " + e, e);
        }
    }

    private byte[] bytes(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }
}
