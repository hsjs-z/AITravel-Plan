package com.travelplatform.notification.application;

import com.travelplatform.notification.domain.DeliveryStatus;
import com.travelplatform.notification.domain.Notification;
import com.travelplatform.notification.domain.NotificationChannel;
import com.travelplatform.notification.domain.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationEmailDispatcherTest {

    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final EmailSender emailSender = mock(EmailSender.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final NotificationEmailDispatcher dispatcher =
            new NotificationEmailDispatcher(repository, emailSender, notificationService, 50);

    @Test
    void deliversPendingEmailAndMarksSent() {
        Notification pending = new Notification();
        pending.setId("n-1");
        pending.setUserId("u-1");
        pending.setTitle("支付成功");
        pending.setBody("body");
        when(repository.findByChannelAndDeliveryStatusOrderByCreatedAtAsc(
                eq(NotificationChannel.EMAIL), eq(DeliveryStatus.PENDING), any(PageRequest.class)))
                .thenReturn(List.of(pending));

        dispatcher.dispatchPendingEmails();

        verify(emailSender).send("u-1", "支付成功", "body");
        verify(notificationService).markEmailSent("n-1");
    }

    @Test
    void keepsEmailPendingOnFailureSoItIsRetried() {
        Notification pending = new Notification();
        pending.setId("n-1");
        pending.setUserId("u-1");
        pending.setTitle("支付成功");
        pending.setBody("body");
        when(repository.findByChannelAndDeliveryStatusOrderByCreatedAtAsc(
                eq(NotificationChannel.EMAIL), eq(DeliveryStatus.PENDING), any(PageRequest.class)))
                .thenReturn(List.of(pending));
        doThrow(new RuntimeException("smtp unavailable")).when(emailSender).send(anyString(), anyString(), anyString());

        dispatcher.dispatchPendingEmails();

        verify(notificationService).recordEmailFailure(eq("n-1"), anyString());
    }
}
