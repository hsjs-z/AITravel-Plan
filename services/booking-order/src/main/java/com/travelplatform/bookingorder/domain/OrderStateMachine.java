package com.travelplatform.bookingorder.domain;

import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;

import java.time.Instant;

/**
 * Order lifecycle: CREATED -> BOOKED -> PAID -> CONFIRMED;
 * terminal states CANCELLED and FAILED.
 */
public final class OrderStateMachine {

    public void booked(Order order) {
        require(order.getStatus() == OrderStatus.CREATED, "only created orders can be booked");
        order.setStatus(OrderStatus.BOOKED);
        order.setBookedAt(Instant.now());
    }

    public void paid(Order order) {
        require(order.getStatus() == OrderStatus.BOOKED, "only booked orders can be paid");
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(Instant.now());
    }

    public void confirmed(Order order) {
        require(order.getStatus() == OrderStatus.PAID, "only paid orders can be confirmed");
        order.setStatus(OrderStatus.CONFIRMED);
        order.setConfirmedAt(Instant.now());
    }

    public void cancel(Order order) {
        require(
                order.getStatus() == OrderStatus.CREATED
                        || order.getStatus() == OrderStatus.BOOKED
                        || order.getStatus() == OrderStatus.PAID,
                "order cannot be cancelled from its current status");
        order.setStatus(OrderStatus.CANCELLED);
    }

    public void fail(Order order, String reason) {
        require(
                order.getStatus() == OrderStatus.CREATED
                        || order.getStatus() == OrderStatus.BOOKED,
                "order cannot fail from its current status");
        order.setStatus(OrderStatus.FAILED);
        order.setFailureReason(reason);
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(ErrorCode.CONFLICT, message);
        }
    }
}
