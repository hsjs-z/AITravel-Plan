package com.travelplatform.bookingorder.application;

import com.travelplatform.bookingorder.domain.BookingSettlementLog;
import com.travelplatform.bookingorder.domain.BookingSettlementLogRepository;
import com.travelplatform.bookingorder.domain.Order;
import com.travelplatform.bookingorder.domain.OrderItem;
import com.travelplatform.bookingorder.domain.OrderItemRepository;
import com.travelplatform.bookingorder.domain.OrderItemStatus;
import com.travelplatform.bookingorder.domain.SettlementStatus;
import com.travelplatform.bookingorder.infrastructure.InventoryClient;
import com.travelplatform.bookingorder.infrastructure.ItineraryClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the one-click joint booking saga: lock the itinerary, lock each
 * item's inventory in turn, and on any failure release everything already
 * locked (compensation must be idempotent).
 */
@Component
public class SagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(SagaOrchestrator.class);

    private final InventoryClient inventoryClient;
    private final ItineraryClient itineraryClient;
    private final BookingSettlementLogRepository logRepository;
    private final OrderItemRepository itemRepository;

    public SagaOrchestrator(InventoryClient inventoryClient,
                            ItineraryClient itineraryClient,
                            BookingSettlementLogRepository logRepository,
                            OrderItemRepository itemRepository) {
        this.inventoryClient = inventoryClient;
        this.itineraryClient = itineraryClient;
        this.logRepository = logRepository;
        this.itemRepository = itemRepository;
    }

    public boolean book(Order order, List<OrderItem> items) {
        try {
            itineraryClient.lock(order.getItineraryId(), order.getUserId());
        } catch (Exception e) {
            record(order, "itinerary.lock", SettlementStatus.FAILED);
            return false;
        }
        record(order, "itinerary.lock", SettlementStatus.SUCCEEDED);

        List<OrderItem> locked = new ArrayList<>();
        for (OrderItem item : items) {
            try {
                InventoryClient.InventoryLockApiResponse response = inventoryClient.lock(
                        new InventoryClient.InventoryLockRequest(
                                item.getResourceId(),
                                item.getDate(),
                                item.getSpecId(),
                                item.getQuantity(),
                                "BOOKING",
                                900,
                                "ORDER",
                                order.getId()));
                item.setLockId(response.data().lockId());
                item.setStatus(OrderItemStatus.BOOKED);
                locked.add(item);
                record(order, "inventory.lock." + item.getResourceId(), SettlementStatus.SUCCEEDED);
            } catch (Exception e) {
                log.warn("inventory lock failed for item {}: {}", item.getResourceId(), e.toString());
                item.setStatus(OrderItemStatus.FAILED);
                record(order, "inventory.lock." + item.getResourceId(), SettlementStatus.FAILED);
                compensate(order, locked);
                return false;
            }
        }
        return true;
    }

    public void cancel(Order order, List<OrderItem> items) {
        for (OrderItem item : items) {
            if (item.getLockId() != null) {
                releaseQuietly(order, item);
            }
        }
        releaseItineraryQuietly(order);
    }

    /**
     * Retries a failed compensation for a still-locked item. Release is
     * idempotent on the inventory side, so repeated retries are safe.
     */
    public void retryCompensation(Order order, OrderItem item) {
        if (item.getLockId() == null || item.getStatus() != OrderItemStatus.BOOKED) {
            return;
        }
        releaseQuietly(order, item);
    }

    private void compensate(Order order, List<OrderItem> locked) {
        for (OrderItem item : locked) {
            releaseQuietly(order, item);
        }
        releaseItineraryQuietly(order);
    }

    private void releaseQuietly(Order order, OrderItem item) {
        try {
            inventoryClient.release(item.getLockId());
            item.setStatus(OrderItemStatus.RELEASED);
            itemRepository.save(item);
            record(order, "inventory.release." + item.getResourceId(), SettlementStatus.COMPENSATED);
        } catch (Exception e) {
            record(order, "inventory.release." + item.getResourceId(), SettlementStatus.COMPENSATING);
        }
    }

    private void releaseItineraryQuietly(Order order) {
        try {
            itineraryClient.release(order.getItineraryId(), order.getUserId());
        } catch (Exception ignored) {
            // itinerary release is best-effort; the order owner will surface it if needed
        }
    }

    private void record(Order order, String step, SettlementStatus status) {
        BookingSettlementLog log = new BookingSettlementLog();
        log.setId(UUID.randomUUID().toString());
        log.setOrderId(order.getId());
        log.setStep(step);
        log.setStatus(status);
        log.setUpdatedAt(Instant.now());
        logRepository.save(log);
    }
}
