package com.travelplatform.notification.application;

import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;
import com.travelplatform.notification.domain.DeliveryStatus;
import com.travelplatform.notification.domain.Notification;
import com.travelplatform.notification.domain.NotificationChannel;
import com.travelplatform.notification.domain.NotificationRepository;
import com.travelplatform.notification.domain.NotificationType;
import com.travelplatform.notification.domain.ReadStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationEventPublisher publisher = mock(NotificationEventPublisher.class);
    private final NotificationService service = new NotificationService(repository, publisher);

    @Test
    void recordCreatesMandatoryInAppAndOptionalEmailCopies() {
        when(repository.findByDedupKey(anyString())).thenReturn(Optional.empty());

        service.record(draft());

        verify(repository, times(2)).save(any(Notification.class));
        verify(publisher, times(1)).notificationSent(any(Notification.class));
    }

    @Test
    void recordIsIdempotentPerBusinessFact() {
        when(repository.findByDedupKey(anyString())).thenReturn(Optional.empty());
        service.record(draft());

        Notification existing = new Notification();
        when(repository.findByDedupKey(anyString())).thenReturn(Optional.of(existing));
        service.record(draft());

        verify(repository, times(2)).save(any(Notification.class));
        verify(publisher, times(1)).notificationSent(any(Notification.class));
    }

    @Test
    void unreadCountOnlyCountsInAppUnread() {
        when(repository.countByUserIdAndChannelAndReadStatus(
                "u-1", NotificationChannel.IN_APP, ReadStatus.UNREAD)).thenReturn(3L);

        assertEquals(3L, service.unreadCount("u-1"));
    }

    @Test
    void markReadRejectsOtherUsersNotification() {
        when(repository.findByIdAndUserId("n-1", "u-2")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.markRead("u-2", "n-1"));

        assertEquals(ErrorCode.NOT_FOUND, ex.getErrorCode());
    }

    @Test
    void markReadFlipsUnreadToRead() {
        Notification unread = new Notification();
        unread.setId("n-1");
        unread.setReadStatus(ReadStatus.UNREAD);
        when(repository.findByIdAndUserId("n-1", "u-1")).thenReturn(Optional.of(unread));

        Notification result = service.markRead("u-1", "n-1");

        assertEquals(ReadStatus.READ, result.getReadStatus());
        assertNotNull(result.getReadAt());
        verify(repository).save(unread);
    }

    @Test
    void emailFailureStaysPendingAndCountsAttempt() {
        Notification pending = new Notification();
        pending.setId("n-1");
        pending.setDeliveryStatus(DeliveryStatus.PENDING);
        pending.setDeliveryAttempts(1);
        when(repository.findById("n-1")).thenReturn(Optional.of(pending));

        service.recordEmailFailure("n-1", "smtp down");

        assertEquals(DeliveryStatus.PENDING, pending.getDeliveryStatus());
        assertEquals(2, pending.getDeliveryAttempts());
        assertEquals("smtp down", pending.getLastError());
        verify(repository).save(pending);
    }

    private NotificationDraft draft() {
        return new NotificationDraft(NotificationType.PAYMENT_SUCCEEDED, "u-1", "order-1",
                "支付成功", "订单 order-1 支付成功。");
    }
}
