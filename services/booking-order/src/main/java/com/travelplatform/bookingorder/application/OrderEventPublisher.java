package com.travelplatform.bookingorder.application;

import com.travelplatform.bookingorder.domain.Order;
import com.travelplatform.bookingorder.domain.OrderItem;
import com.travelplatform.common.outbox.EventTypes;
import com.travelplatform.common.outbox.OutboxRecorder;
import com.travelplatform.common.outbox.OutboxTopics;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Publishes order lifecycle events through the transactional outbox (T039).
 * OrderFailed carries the resources that could not be booked so the user can be
 * told which item failed and what the final outcome was (FR-016).
 */
@Component
public class OrderEventPublisher {

    private static final String AGGREGATE_TYPE = "ORDER";

    private final OutboxRecorder recorder;

    public OrderEventPublisher(OutboxRecorder recorder) {
        this.recorder = recorder;
    }

    public void orderCreated(Order order, int itemCount) {
        record(EventTypes.ORDER_CREATED, order, new OrderCreated(
                order.getId(), order.getUserId(), order.getItineraryId(), order.getStatus().name(),
                order.getTotalAmount(), itemCount));
    }

    public void orderBooked(Order order, List<OrderItem> items) {
        record(EventTypes.ORDER_BOOKED, order, new OrderBooked(
                order.getId(), order.getUserId(), order.getItineraryId(), order.getStatus().name(),
                order.getTotalAmount(), items.stream().map(Item::of).toList()));
    }

    public void orderPaid(Order order) {
        record(EventTypes.ORDER_PAID, order, new OrderPaid(
                order.getId(), order.getUserId(), order.getItineraryId(), order.getStatus().name(),
                order.getTotalAmount(), order.getPaymentId()));
    }

    public void orderConfirmed(Order order) {
        record(EventTypes.ORDER_CONFIRMED, order, new OrderConfirmed(
                order.getId(), order.getUserId(), order.getItineraryId(), order.getStatus().name(),
                order.getTotalAmount()));
    }

    public void orderCancelled(Order order) {
        record(EventTypes.ORDER_CANCELLED, order, new OrderCancelled(
                order.getId(), order.getUserId(), order.getItineraryId(), order.getStatus().name(),
                order.getTotalAmount(), order.getRefundId()));
    }

    public void orderFailed(Order order, List<OrderItem> failedItems) {
        record(EventTypes.ORDER_FAILED, order, new OrderFailed(
                order.getId(), order.getUserId(), order.getItineraryId(), order.getStatus().name(),
                order.getFailureReason(), failedItems.stream().map(Item::of).toList()));
    }

    private void record(String eventType, Order order, Object payload) {
        recorder.record(OutboxTopics.ORDER_EVENTS, eventType, AGGREGATE_TYPE, order.getId(), payload);
    }

    public record Item(String resourceId, String specId, LocalDate date, int quantity, Long unitPrice) {

        static Item of(OrderItem item) {
            return new Item(item.getResourceId(), item.getSpecId(), item.getDate(),
                    item.getQuantity(), item.getUnitPrice());
        }
    }

    public record OrderCreated(String orderId, String userId, String itineraryId, String status,
                               Long totalAmount, int itemCount) {
    }

    public record OrderBooked(String orderId, String userId, String itineraryId, String status,
                              Long totalAmount, List<Item> items) {
    }

    public record OrderPaid(String orderId, String userId, String itineraryId, String status,
                            Long totalAmount, String paymentId) {
    }

    public record OrderConfirmed(String orderId, String userId, String itineraryId, String status,
                                 Long totalAmount) {
    }

    public record OrderCancelled(String orderId, String userId, String itineraryId, String status,
                                 Long totalAmount, String refundId) {
    }

    public record OrderFailed(String orderId, String userId, String itineraryId, String status,
                              String failureReason, List<Item> failedItems) {
    }
}
