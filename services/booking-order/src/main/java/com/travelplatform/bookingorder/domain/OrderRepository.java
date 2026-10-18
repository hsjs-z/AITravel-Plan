package com.travelplatform.bookingorder.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, String> {

    Optional<Order> findByIdAndUserId(String id, String userId);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    List<Order> findByUserIdOrderByCreatedAtDesc(String userId);

    List<Order> findByUserIdAndItineraryIdIn(String userId, Collection<String> itineraryIds);

    List<Order> findByStatusIn(List<OrderStatus> statuses);

    List<Order> findByStatus(OrderStatus status);
}
