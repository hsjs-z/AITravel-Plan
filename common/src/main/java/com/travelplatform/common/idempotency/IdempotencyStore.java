package com.travelplatform.common.idempotency;

/**
 * Port for idempotency. Implementations must be safe under concurrency
 * (atomic acquire), otherwise duplicate work can slip through.
 */
public interface IdempotencyStore {

    boolean tryAcquire(String key);

    boolean isProcessed(String key);
}
