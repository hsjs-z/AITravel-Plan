package com.travelplatform.bookingorder.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelplatform.bookingorder.domain.Order;
import com.travelplatform.bookingorder.domain.OrderItemRepository;
import com.travelplatform.bookingorder.domain.OrderRepository;
import com.travelplatform.bookingorder.domain.OrderStatus;
import com.travelplatform.bookingorder.infrastructure.InventoryClient;
import com.travelplatform.bookingorder.infrastructure.PaymentClient;
import com.travelplatform.common.outbox.OutboxRecorder;
import com.travelplatform.common.outbox.OutboxStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderSummaryTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderService service = new OrderService(
            orderRepository,
            mock(OrderItemRepository.class),
            mock(SagaOrchestrator.class),
            mock(PaymentClient.class),
            new OrderEventPublisher(new OutboxRecorder(
                    mock(OutboxStore.class), new ObjectMapper().findAndRegisterModules())),
            mock(InventoryClient.class));

    @Test
    void countsActiveAndCompletedOrdersPerItinerary() {
        List<String> ids = List.of("it-a", "it-b", "it-c");
        when(orderRepository.findByUserIdAndItineraryIdIn("u-1", ids)).thenReturn(List.of(
                order("it-a", OrderStatus.BOOKED),
                order("it-a", OrderStatus.CONFIRMED),
                order("it-b", OrderStatus.CANCELLED),
                order("it-b", OrderStatus.FAILED)));

        List<OrderService.OrderSummary> summaries = service.summaries("u-1", ids);

        assertEquals(3, summaries.size());
        assertEquals(new OrderService.OrderSummary("it-a", 1, 1), summaries.get(0));
        assertEquals(new OrderService.OrderSummary("it-b", 0, 0), summaries.get(1));
        assertEquals(new OrderService.OrderSummary("it-c", 0, 0), summaries.get(2));
        verify(orderRepository).findByUserIdAndItineraryIdIn("u-1", ids);
    }

    private Order order(String itineraryId, OrderStatus status) {
        Order order = new Order();
        order.setItineraryId(itineraryId);
        order.setStatus(status);
        return order;
    }
}
