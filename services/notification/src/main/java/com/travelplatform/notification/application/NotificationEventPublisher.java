package com.travelplatform.notification.application;

import com.travelplatform.common.outbox.EventTypes;
import com.travelplatform.common.outbox.OutboxRecorder;
import com.travelplatform.common.outbox.OutboxTopics;
import com.travelplatform.notification.domain.Notification;
import org.springframework.stereotype.Component;

/** Publishes NotificationSent through the transactional outbox (contracts/events.md). */
@Component
public class NotificationEventPublisher {

    private final OutboxRecorder recorder;

    public NotificationEventPublisher(OutboxRecorder recorder) {
        this.recorder = recorder;
    }

    public void notificationSent(Notification notification) {
        recorder.record(OutboxTopics.NOTIFICATION_EVENTS, EventTypes.NOTIFICATION_SENT, "NOTIFICATION",
                notification.getId(), new NotificationSent(
                        notification.getId(), notification.getUserId(), notification.getType().name(),
                        notification.getChannel().name(), notification.getBusinessRef()));
    }

    public record NotificationSent(
            String notificationId, String userId, String type, String channel, String businessRef) {
    }
}
