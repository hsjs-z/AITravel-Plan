package com.travelplatform.notification.application;

import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;
import com.travelplatform.notification.domain.DeliveryStatus;
import com.travelplatform.notification.domain.Notification;
import com.travelplatform.notification.domain.NotificationChannel;
import com.travelplatform.notification.domain.NotificationRepository;
import com.travelplatform.notification.domain.ReadStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Notification reads/writes (T058). Every read is scoped to the owner; content of
 * other users is never returned, mirroring the platform's anti-enumeration
 * convention (FR-013).
 *
 * <p>Persisting a draft creates the mandatory in-app copy (delivered by definition:
 * the row itself is the mailbox) and an optional email copy that stays
 * {@code PENDING} until the email dispatcher confirms it (FR-033, FR-034).
 */
@Service
public class NotificationService {

    private final NotificationRepository repository;
    private final NotificationEventPublisher publisher;

    public NotificationService(NotificationRepository repository, NotificationEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    public List<Notification> list(String userId) {
        return repository.findByUserIdAndChannelOrderByCreatedAtDesc(userId, NotificationChannel.IN_APP);
    }

    public long unreadCount(String userId) {
        return repository.countByUserIdAndChannelAndReadStatus(
                userId, NotificationChannel.IN_APP, ReadStatus.UNREAD);
    }

    @Transactional
    public Notification markRead(String userId, String notificationId) {
        Notification notification = repository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "notification not found"));
        if (notification.getReadStatus() == ReadStatus.UNREAD) {
            notification.setReadStatus(ReadStatus.READ);
            notification.setReadAt(Instant.now());
            repository.save(notification);
        }
        return notification;
    }

    /** Persists the in-app (mandatory) and email (optional, retry-to-complete) copies. */
    @Transactional
    public void record(NotificationDraft draft) {
        create(NotificationChannel.IN_APP, draft, DeliveryStatus.SENT);
        create(NotificationChannel.EMAIL, draft, DeliveryStatus.PENDING);
    }

    private void create(NotificationChannel channel, NotificationDraft draft, DeliveryStatus deliveryStatus) {
        String dedupKey = dedupKey(draft, channel);
        if (repository.findByDedupKey(dedupKey).isPresent()) {
            return;
        }
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID().toString());
        notification.setUserId(draft.userId());
        notification.setType(draft.type());
        notification.setChannel(channel);
        notification.setBusinessRef(draft.businessRef());
        notification.setDedupKey(dedupKey);
        notification.setReadStatus(ReadStatus.UNREAD);
        notification.setTitle(draft.title());
        notification.setBody(draft.body());
        notification.setDeliveryStatus(deliveryStatus);
        notification.setDeliveryAttempts(deliveryStatus == DeliveryStatus.SENT ? 1 : 0);
        notification.setCreatedAt(Instant.now());
        repository.save(notification);
        if (channel == NotificationChannel.IN_APP) {
            publisher.notificationSent(notification);
        }
    }

    /** Marks a delivered email; emits NotificationSent so the channel fact is observable. */
    @Transactional
    public void markEmailSent(String notificationId) {
        Notification notification = repository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "notification not found"));
        notification.setDeliveryStatus(DeliveryStatus.SENT);
        notification.setDeliveryAttempts(notification.getDeliveryAttempts() + 1);
        notification.setLastError(null);
        repository.save(notification);
        publisher.notificationSent(notification);
    }

    /** Records a failed email attempt; the row stays PENDING for the next tick (FR-034). */
    @Transactional
    public void recordEmailFailure(String notificationId, String error) {
        Notification notification = repository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "notification not found"));
        notification.setDeliveryAttempts(notification.getDeliveryAttempts() + 1);
        notification.setLastError(truncate(error, 500));
        repository.save(notification);
    }

    private String dedupKey(NotificationDraft draft, NotificationChannel channel) {
        String base = draft.type().name() + ":" + draft.userId() + ":" + draft.businessRef();
        return channel == NotificationChannel.EMAIL ? base + ":email" : base;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
