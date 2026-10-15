package com.travelplatform.common.outbox;

public enum OutboxStatus {

    /** Written with the business change, not yet handed to the broker. */
    NEW,

    /** Confirmed by the broker; the row is kept for audit. */
    SENT
}
