package com.travelplatform.notification.api;

import com.travelplatform.common.api.ApiResponse;
import com.travelplatform.notification.application.NotificationService;
import com.travelplatform.notification.domain.Notification;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Notification endpoints (T059). Contract: contracts/notification.md. */
@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/notifications")
    public ApiResponse<List<NotificationResponse>> list(@RequestHeader("X-User-Id") String userId) {
        return ApiResponse.success(notificationService.list(userId).stream()
                .map(NotificationController::toResponse)
                .toList());
    }

    @GetMapping("/notifications/unread-count")
    public ApiResponse<UnreadCountResponse> unreadCount(@RequestHeader("X-User-Id") String userId) {
        return ApiResponse.success(new UnreadCountResponse(notificationService.unreadCount(userId)));
    }

    @PostMapping("/notifications/{notificationId}/read")
    public ApiResponse<NotificationResponse> markRead(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("notificationId") String notificationId) {
        return ApiResponse.success(toResponse(notificationService.markRead(userId, notificationId)));
    }

    private static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getChannel().name(),
                notification.getBusinessRef(),
                notification.getTitle(),
                notification.getBody(),
                notification.getReadStatus().name(),
                notification.getCreatedAt(),
                notification.getReadAt());
    }

    public record NotificationResponse(
            String id,
            String type,
            String channel,
            String businessRef,
            String title,
            String body,
            String readStatus,
            Instant createdAt,
            Instant readAt) {
    }

    public record UnreadCountResponse(long unread) {
    }
}
