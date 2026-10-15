package com.travelplatform.common.outbox;

/** Event type names from contracts/events.md. */
public final class EventTypes {

    public static final String PLANNING_SUCCEEDED = "PlanningSucceeded";
    public static final String PLANNING_FAILED = "PlanningFailed";

    public static final String ITINERARY_CONFIRMED = "ItineraryConfirmed";
    public static final String ITINERARY_CHANGED = "ItineraryChanged";

    public static final String ORDER_CREATED = "OrderCreated";
    public static final String ORDER_BOOKED = "OrderBooked";
    public static final String ORDER_PAID = "OrderPaid";
    public static final String ORDER_CONFIRMED = "OrderConfirmed";
    public static final String ORDER_CANCELLED = "OrderCancelled";
    public static final String ORDER_FAILED = "OrderFailed";

    public static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String REFUND_SUCCEEDED = "RefundSucceeded";

    public static final String INVENTORY_LOCKED = "InventoryLocked";
    public static final String INVENTORY_RELEASED = "InventoryReleased";
    public static final String INVENTORY_SOLD_OUT = "InventorySoldOut";

    public static final String NOTIFICATION_SENT = "NotificationSent";

    private EventTypes() {
    }
}
