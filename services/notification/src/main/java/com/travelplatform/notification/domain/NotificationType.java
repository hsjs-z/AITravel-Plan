package com.travelplatform.notification.domain;

/** Notification kind, a superset of GLOSSARY「通知」类型以覆盖退款与支付失败。 */
public enum NotificationType {
    BOOKING_SUCCEEDED,
    BOOKING_FAILED,
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,
    REFUND_SUCCEEDED,
    ORDER_CONFIRMED,
    ORDER_CANCELLED,
    ITINERARY_CHANGED
}
