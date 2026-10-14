package com.travelplatform.bookingorder.application;

import com.travelplatform.bookingorder.domain.BookingSettlementLogRepository;
import com.travelplatform.bookingorder.domain.Order;
import com.travelplatform.bookingorder.domain.OrderItem;
import com.travelplatform.bookingorder.domain.OrderItemStatus;
import com.travelplatform.bookingorder.infrastructure.InventoryClient;
import com.travelplatform.bookingorder.infrastructure.ItineraryClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SagaOrchestratorTest {

    private final InventoryClient inventoryClient = mock(InventoryClient.class);
    private final ItineraryClient itineraryClient = mock(ItineraryClient.class);
    private final BookingSettlementLogRepository logRepository = mock(BookingSettlementLogRepository.class);

    private final SagaOrchestrator orchestrator =
            new SagaOrchestrator(inventoryClient, itineraryClient, logRepository);

    @Test
    void partialFailureCompensatesAlreadyLockedItems() {
        Order order = new Order();
        order.setId("order-1");
        order.setItineraryId("itinerary-1");
        order.setUserId("user-1");

        OrderItem first = item("r-1", 1);
        OrderItem second = item("r-2", 1);

        when(inventoryClient.lock(any())).thenAnswer(invocation -> {
            InventoryClient.InventoryLockRequest request = invocation.getArgument(0);
            if ("r-1".equals(request.resourceId())) {
                return new InventoryClient.InventoryLockApiResponse(
                        0, "OK", new InventoryClient.InventoryLockData("lock-1"));
            }
            throw new RuntimeException("sold out");
        });

        boolean result = orchestrator.book(order, List.of(first, second));

        assertFalse(result);
        assertEquals(OrderItemStatus.BOOKED, first.getStatus());
        assertEquals("lock-1", first.getLockId());
        assertEquals(OrderItemStatus.FAILED, second.getStatus());

        verify(inventoryClient).release("lock-1");
        verify(itineraryClient).release("itinerary-1", "user-1");
    }

    private OrderItem item(String resourceId, int quantity) {
        OrderItem item = new OrderItem();
        item.setId(resourceId + "-item");
        item.setOrderId("order-1");
        item.setResourceId(resourceId);
        item.setSpecId("s-1");
        item.setDate(LocalDate.of(2026, 11, 1));
        item.setQuantity(quantity);
        item.setUnitPrice(100L);
        item.setStatus(OrderItemStatus.PENDING);
        return item;
    }
}
