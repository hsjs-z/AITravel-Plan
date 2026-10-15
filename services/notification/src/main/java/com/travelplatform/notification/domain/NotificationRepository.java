package com.travelplatform.notification.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    List<Notification> findByUserIdAndChannelOrderByCreatedAtDesc(
            String userId, NotificationChannel channel);

    long countByUserIdAndChannelAndReadStatus(
            String userId, NotificationChannel channel, ReadStatus readStatus);

    Optional<Notification> findByIdAndUserId(String id, String userId);

    Optional<Notification> findByDedupKey(String dedupKey);

    List<Notification> findByChannelAndDeliveryStatusOrderByCreatedAtAsc(
            NotificationChannel channel, DeliveryStatus status, Pageable pageable);
}
