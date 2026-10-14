package com.travelplatform.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, String> {

    Optional<Refund> findByIdempotencyKey(String idempotencyKey);

    List<Refund> findByOrderId(String orderId);
}
