package com.travelplatform.bookingorder.application;

import com.travelplatform.bookingorder.domain.Order;
import com.travelplatform.bookingorder.domain.OrderRepository;
import com.travelplatform.bookingorder.domain.OrderStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Drives time-based order transitions: auto-confirm paid orders (ADR-0001)
 * and auto-cancel booked orders past the unpaid hold window (T037).
 */
@Component
public class OrderLifecycleScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final long timeoutSeconds;

    public OrderLifecycleScheduler(OrderRepository orderRepository,
                                   OrderService orderService,
                                   @Value("${booking.timeout-seconds:900}") long timeoutSeconds) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Scheduled(fixedDelayString = "${booking.auto-confirm-ms:10000}")
    public void reconcilePaidOrders() {
        for (Order order : orderRepository.findByStatusIn(
                List.of(OrderStatus.PAID, OrderStatus.CONFIRMED))) {
            if (order.getRefundId() != null || orderService.hasSucceededRefund(order.getId())) {
                orderService.convergeToCancelled(order);
            } else if (order.getStatus() == OrderStatus.PAID) {
                orderService.autoConfirm(order);
            }
        }
    }

    @Scheduled(fixedDelayString = "${booking.order-timeout-ms:30000}")
    public void cancelExpiredBookedOrders() {
        Instant cutoff = Instant.now().minusSeconds(timeoutSeconds);
        for (Order order : orderRepository.findByStatus(OrderStatus.BOOKED)) {
            if (order.getBookedAt() != null && order.getBookedAt().isBefore(cutoff)) {
                orderService.cancelExpired(order);
            }
        }
    }
}
