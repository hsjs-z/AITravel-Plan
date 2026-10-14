package com.travelplatform.common.outbox;

/**
 * Port for the outbox relay. Implementations deliver to the configured broker.
 */
public interface OutboxPublisher {

    void publish(String topic, String key, String payload);
}
