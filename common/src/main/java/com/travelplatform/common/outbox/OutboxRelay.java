package com.travelplatform.common.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.List;

/**
 * Polling publisher: hands pending outbox rows to the broker and marks them sent.
 * A delivery failure leaves the row {@code NEW} and records the attempt, so the next
 * tick retries — the event is never dropped (宪法 IV, FR-034).
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxStore store;
    private final OutboxPublisher publisher;
    private final int batchSize;

    public OutboxRelay(OutboxStore store, OutboxPublisher publisher, int batchSize) {
        this.store = store;
        this.publisher = publisher;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.interval-ms:1000}")
    public void relay() {
        List<OutboxMessage> pending;
        try {
            pending = store.findPending(batchSize);
        } catch (Exception e) {
            log.warn("outbox relay could not read pending events: {}", e.toString());
            return;
        }
        for (OutboxMessage message : pending) {
            try {
                publisher.publish(message);
                store.markSent(message.getId(), Instant.now());
            } catch (Exception e) {
                log.warn("outbox relay failed to publish event {} of type {}: {}",
                        message.getId(), message.getEventType(), e.toString());
                markFailure(message, e);
            }
        }
    }

    private void markFailure(OutboxMessage message, Exception e) {
        try {
            store.markAttemptFailure(message.getId(), e.toString());
        } catch (Exception nested) {
            log.warn("outbox relay could not record failure for event {}: {}",
                    message.getId(), nested.toString());
        }
    }
}
