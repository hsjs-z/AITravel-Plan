package com.travelplatform.bookingorder.application;

import com.travelplatform.bookingorder.domain.Order;
import com.travelplatform.bookingorder.domain.OrderRepository;
import com.travelplatform.bookingorder.domain.OrderStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Drives time-based order transitions: auto-confirm paid orders (ADR-0001)
 * and auto-cancel booked orders past the unpaid hold window (T037).
 */
@Component
public class OrderLifecycleScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final long timeoutMinutes;

    public OrderLifecycleScheduler(OrderRepository orderRepository,
                                   OrderService orderService,
                                   @Value("${booking.order-timeout-minutes:15}") long timeoutMinutes) {
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.timeoutMinutes = timeoutMinutes;
    }

    @Scheduled(fixedDelayString = "${booking.auto-confirm-ms:10000}")
    public void autoConfirmPaidOrders() {
        for (Order order : orderRepository.findByStatus(OrderStatus.PAID)) {
            orderService.autoConfirm(order);
        }
    }

    @Scheduled(fixedDelayString = "${booking.order-timeout-ms:30000}")
    public void cancelExpiredBookedOrders() {
        Instant cutoff = Instant.now().minus(timeoutMinutes, ChronoUnit.MINUTES);
        for (Order order : orderRepository.findByStatus(OrderStatus.BOOKED)) {
            if (order.getBookedAt() != null && order.getBookedAt().isBefore(cutoff)) {
                orderService.cancelExpired(order);
            }
        }
    }
}
