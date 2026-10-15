package com.travelplatform.common.outbox;

import java.time.Instant;
import java.util.List;

/**
 * Persistence port for the transactional outbox. Each service owns its own
 * {@code outbox_record} table (business modules must never share tables, 宪法 I),
 * so the port is implemented per service on top of {@link JpaOutboxStore}.
 */
public interface OutboxStore {

    /**
     * Writes the event row. Call this from inside the transaction that performs the
     * business change so the row and the change commit or roll back together (宪法 IV).
     */
    void persist(OutboxMessage message);

    /** Oldest-first pending rows, at most {@code limit}. */
    List<OutboxMessage> findPending(int limit);

    void markSent(String id, Instant publishedAt);

    /** Records a failed delivery attempt; the row stays {@code NEW} for the next relay tick. */
    void markAttemptFailure(String id, String error);
}
