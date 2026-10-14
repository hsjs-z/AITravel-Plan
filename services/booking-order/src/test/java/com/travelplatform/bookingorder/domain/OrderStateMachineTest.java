package com.travelplatform.bookingorder.domain;

import com.travelplatform.common.api.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 订单状态机测试类
 * 用于测试订单状态转换的各种场景
 */
class OrderStateMachineTest {

    // 创建订单状态机实例
    private final OrderStateMachine stateMachine = new OrderStateMachine();

    /**
     * 测试订单从创建到预订到支付再到确认的完整流程
     * 验证每个状态转换的正确性
     */
    @Test
    void createdBookedPaidConfirmedFlow() {
        // 创建新订单并设置初始状态为已创建
        Order order = new Order();
        order.setStatus(OrderStatus.CREATED);

        // 测试状态转换：CREATED -> BOOKED
        stateMachine.booked(order);
        assertEquals(OrderStatus.BOOKED, order.getStatus());
        // 测试状态转换：BOOKED -> PAID
        stateMachine.paid(order);
        assertEquals(OrderStatus.PAID, order.getStatus());
        // 测试状态转换：PAID -> CONFIRMED
        stateMachine.confirmed(order);
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }

    /**
     * 测试预订状态的订单可以被取消
     * 验证取消操作的正确性
     */
    @Test
    void cancelIsAllowedForBookedOrder() {
        // 创建订单并设置为预订状态
        Order order = new Order();
        order.setStatus(OrderStatus.BOOKED);
        // 执行取消操作
        stateMachine.cancel(order);
        // 验证订单状态是否已更改为已取消
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    /**
     * 测试非法的状态转换是否会抛出异常
     * 验证状态机的规则约束
     */
    @Test
    void illegalTransitionThrows() {
        // 创建新订单并设置初始状态为已创建
        Order order = new Order();
        order.setStatus(OrderStatus.CREATED);
        // 验证尝试从CREATED状态直接转换到PAID状态是否会抛出业务异常
        assertThrows(BusinessException.class, () -> stateMachine.paid(order));
    }
}
