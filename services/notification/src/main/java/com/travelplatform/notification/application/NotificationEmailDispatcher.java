package com.travelplatform.notification.application;

import com.travelplatform.notification.domain.DeliveryStatus;
import com.travelplatform.notification.domain.Notification;
import com.travelplatform.notification.domain.NotificationChannel;
import com.travelplatform.notification.domain.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Retries undelivered email copies. A failure keeps the row PENDING so the next tick
 * retries — the "下游短时不可用、恢复后补齐" property of FR-034. The in-app copy is
 * already delivered by the time this runs, so email is purely additive.
 */
@Component
public class NotificationEmailDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationEmailDispatcher.class);

    private final NotificationRepository repository;
    private final EmailSender emailSender;
    private final NotificationService notificationService;
    private final int batchSize;

    public NotificationEmailDispatcher(NotificationRepository repository,
                                       EmailSender emailSender,
                                       NotificationService notificationService,
                                       @Value("${notification.email.batch-size:50}") int batchSize) {
        this.repository = repository;
        this.emailSender = emailSender;
        this.notificationService = notificationService;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${notification.email.retry-ms:15000}")
    public void dispatchPendingEmails() {
        List<Notification> pending = repository.findByChannelAndDeliveryStatusOrderByCreatedAtAsc(
                NotificationChannel.EMAIL, DeliveryStatus.PENDING, PageRequest.of(0, batchSize));
        for (Notification notification : pending) {
            try {
                emailSender.send(notification.getUserId(), notification.getTitle(), notification.getBody());
                notificationService.markEmailSent(notification.getId());
            } catch (Exception e) {
                log.warn("email delivery failed for notification {}: {}", notification.getId(), e.toString());
                notificationService.recordEmailFailure(notification.getId(), e.toString());
            }
        }
    }
}
