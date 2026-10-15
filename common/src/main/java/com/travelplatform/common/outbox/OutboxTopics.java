package com.travelplatform.common.outbox;

/** Topic names from contracts/events.md — producers and consumers share these. */
public final class OutboxTopics {

    public static final String PLANNING_EVENTS = "planning.events";
    public static final String ITINERARY_EVENTS = "itinerary.events";
    public static final String ORDER_EVENTS = "order.events";
    public static final String PAYMENT_EVENTS = "payment.events";
    public static final String INVENTORY_EVENTS = "inventory.events";
    public static final String NOTIFICATION_EVENTS = "notification.events";

    private OutboxTopics() {
    }
}
