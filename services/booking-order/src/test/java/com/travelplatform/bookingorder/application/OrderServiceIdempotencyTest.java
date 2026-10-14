package com.travelplatform.bookingorder.application;

import com.travelplatform.bookingorder.api.OrderController;
import com.travelplatform.bookingorder.domain.Order;
import com.travelplatform.bookingorder.domain.OrderItemRepository;
import com.travelplatform.bookingorder.domain.OrderRepository;
import com.travelplatform.bookingorder.domain.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceIdempotencyTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderItemRepository itemRepository = mock(OrderItemRepository.class);
    private final SagaOrchestrator orchestrator = mock(SagaOrchestrator.class);

    private final OrderService service = new OrderService(orderRepository, itemRepository, orchestrator);

    @Test
    void duplicateSubmissionReturnsExistingOrderWithoutRerunningSaga() {
        Order existing = new Order();
        existing.setId("order-1");
        existing.setStatus(OrderStatus.BOOKED);
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        Order result = service.create("user-1", request("itinerary-1", "key-1"));

        assertEquals("order-1", result.getId());
        verify(orchestrator, never()).book(any(), any());
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicateFallsBackToExistingOnUniqueConstraintViolation() {
        Order existing = new Order();
        existing.setId("order-1");
        when(orderRepository.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(orderRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        Order result = service.create("user-1", request("itinerary-1", "key-1"));

        assertEquals("order-1", result.getId());
        verify(orchestrator, never()).book(any(), any());
    }

    @Test
    void newOrderRunsSagaExactlyOnce() {
        when(orderRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(orderRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(orchestrator.book(any(), any())).thenReturn(true);

        Order result = service.create("user-1", request("itinerary-1", null));

        assertEquals(OrderStatus.BOOKED, result.getStatus());
        verify(orchestrator).book(any(), any());
    }

    private OrderController.CreateOrderRequest request(String itineraryId, String idempotencyKey) {
        return new OrderController.CreateOrderRequest(
                itineraryId,
                idempotencyKey,
                List.of(new OrderController.OrderItemRequest(
                        "r-1", "s-1", LocalDate.of(2026, 11, 1), 1, 100L)));
    }
}
