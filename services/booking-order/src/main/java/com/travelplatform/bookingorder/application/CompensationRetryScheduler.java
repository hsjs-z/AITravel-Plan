package com.travelplatform.bookingorder.application;

import com.travelplatform.bookingorder.domain.Order;
import com.travelplatform.bookingorder.domain.OrderItem;
import com.travelplatform.bookingorder.domain.OrderItemRepository;
import com.travelplatform.bookingorder.domain.OrderItemStatus;
import com.travelplatform.bookingorder.domain.OrderRepository;
import com.travelplatform.bookingorder.domain.OrderStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Owns the retry of failed saga compensations (FR-016/FR-017): periodically
 * re-releases any still-locked item whose order has already failed or been
 * cancelled, until the release succeeds. Inventory release is idempotent.
 */
@Component
public class CompensationRetryScheduler {

    private final OrderRepository orderRepository;
    private final OrderItemRepository itemRepository;
    private final SagaOrchestrator orchestrator;

    public CompensationRetryScheduler(OrderRepository orderRepository,
                                      OrderItemRepository itemRepository,
                                      SagaOrchestrator orchestrator) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.orchestrator = orchestrator;
    }

    @Scheduled(fixedDelayString = "${booking.compensation-retry-ms:30000}")
    public void retryPendingCompensations() {
        List<Order> orders = orderRepository.findByStatusIn(
                List.of(OrderStatus.FAILED, OrderStatus.CANCELLED));
        for (Order order : orders) {
            for (OrderItem item : itemRepository.findByOrderId(order.getId())) {
                if (item.getLockId() != null && item.getStatus() == OrderItemStatus.BOOKED) {
                    orchestrator.retryCompensation(order, item);
                }
            }
        }
    }
}
