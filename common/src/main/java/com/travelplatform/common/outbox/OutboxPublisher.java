package com.travelplatform.common.outbox;

/**
 * Port for the outbox relay. Implementations deliver the persisted row to the
 * configured broker and must fail loudly when the broker has not acknowledged it,
 * so the relay can retry instead of losing the event.
 */
public interface OutboxPublisher {

    void publish(OutboxMessage message);
}
