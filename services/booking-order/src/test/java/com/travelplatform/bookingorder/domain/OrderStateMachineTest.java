package com.travelplatform.bookingorder.domain;

import com.travelplatform.common.api.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderStateMachineTest {

    private final OrderStateMachine stateMachine = new OrderStateMachine();

    @Test
    void createdBookedPaidConfirmedFlow() {
        Order order = new Order();
        order.setStatus(OrderStatus.CREATED);

        stateMachine.booked(order);
        assertEquals(OrderStatus.BOOKED, order.getStatus());
        stateMachine.paid(order);
        assertEquals(OrderStatus.PAID, order.getStatus());
        stateMachine.confirmed(order);
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }

    @Test
    void cancelIsAllowedForBookedOrder() {
        Order order = new Order();
        order.setStatus(OrderStatus.BOOKED);
        stateMachine.cancel(order);
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void illegalTransitionThrows() {
        Order order = new Order();
        order.setStatus(OrderStatus.CREATED);
        assertThrows(BusinessException.class, () -> stateMachine.paid(order));
    }
}
