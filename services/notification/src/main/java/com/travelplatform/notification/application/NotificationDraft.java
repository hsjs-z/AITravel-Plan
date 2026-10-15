package com.travelplatform.notification.application;

import com.travelplatform.notification.domain.NotificationType;

/**
 * A notification ready to persist. {@code businessRef} is the business object the
 * notification is about (order / payment / itinerary); it also scopes deduplication.
 */
public record NotificationDraft(
        NotificationType type,
        String userId,
        String businessRef,
        String title,
        String body) {
}
