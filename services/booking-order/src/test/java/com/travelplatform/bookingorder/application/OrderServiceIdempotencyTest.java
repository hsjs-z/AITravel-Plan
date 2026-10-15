package com.travelplatform.bookingorder.application;

import com.travelplatform.bookingorder.api.OrderController;
import com.travelplatform.bookingorder.domain.Order;
import com.travelplatform.bookingorder.domain.OrderItemRepository;
import com.travelplatform.bookingorder.domain.OrderRepository;
import com.travelplatform.bookingorder.domain.OrderStatus;
import com.travelplatform.bookingorder.infrastructure.PaymentClient;
import com.travelplatform.common.outbox.OutboxRecorder;
import com.travelplatform.common.outbox.OutboxStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测试类：用于测试OrderService的幂等性功能
 * 主要测试三种场景：重复提交、并发重复提交和新订单创建
 */
class OrderServiceIdempotencyTest {

    // 模拟仓库对象
    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final OrderItemRepository itemRepository = mock(OrderItemRepository.class);
    private final SagaOrchestrator orchestrator = mock(SagaOrchestrator.class);
    private final PaymentClient paymentClient = mock(PaymentClient.class);

    private final OrderEventPublisher events =
            new OrderEventPublisher(new OutboxRecorder(
                    mock(OutboxStore.class), new ObjectMapper().findAndRegisterModules()));

    // 待测试的OrderService实例
    private final OrderService service =
            new OrderService(orderRepository, itemRepository, orchestrator, paymentClient, events);

    /**
     * 测试重复提交时返回已存在的订单且不重新运行Saga
     * 场景：使用相同的幂等键提交订单时，应返回已存在的订单
     */
    @Test
    void duplicateSubmissionReturnsExistingOrderWithoutRerunningSaga() {
        // 创建并设置已存在的订单
        Order existing = new Order();
        existing.setId("order-1");
        existing.setStatus(OrderStatus.BOOKED);
        // 模拟仓库返回已存在的订单
        when(orderRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        // 使用相同的幂等键创建订单
        Order result = service.create("user-1", request("itinerary-1", "key-1"));

        // 验证返回的是已存在的订单ID
        assertEquals("order-1", result.getId());
        // 验证Saga没有重新运行
        verify(orchestrator, never()).book(any(), any());
        // 验证订单没有被保存
        verify(orderRepository, never()).saveAndFlush(any());
    }

    /**
     * 测试并发重复提交时在唯一约束冲突回退到已存在的订单
     * 场景：多个线程同时使用相同的幂等键创建订单时，应回退到已存在的订单
     */
    @Test
    void concurrentDuplicateFallsBackToExistingOnUniqueConstraintViolation() {
        // 创建已存在的订单
        Order existing = new Order();
        existing.setId("order-1");
        // 模拟仓库第一次返回空（新订单），第二次返回已存在的订单
        when(orderRepository.findByIdempotencyKey("key-1"))
                .thenReturn(Optional.empty(), Optional.of(existing));
        // 模拟保存时抛出唯一约束冲突异常
        when(orderRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        // 尝试创建订单
        Order result = service.create("user-1", request("itinerary-1", "key-1"));

        // 验证返回的是已存在的订单ID
        assertEquals("order-1", result.getId());
        // 验证Saga没有运行
        verify(orchestrator, never()).book(any(), any());
    }

    /**
     * 测试新订单只运行一次Saga
     * 场景：创建新订单时，应正确运行Saga并返回已预订状态的订单
     */
    @Test
    void newOrderRunsSagaExactlyOnce() {
        // 模拟仓库返回空（表示是新订单）
        when(orderRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        // 模拟保存订单并返回相同的订单对象
        when(orderRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        // 模拟Saga执行成功
        when(orchestrator.book(any(), any())).thenReturn(true);

        // 创建新订单
        Order result = service.create("user-1", request("itinerary-1", null));

        // 验证订单状态为已预订
        assertEquals(OrderStatus.BOOKED, result.getStatus());
        // 验证Saga被调用了一次
        verify(orchestrator).book(any(), any());
    }

    /**
     * 辅助方法：创建创建订单请求对象
     * @param itineraryId 行程ID
     * @param idempotencyKey 幂等键
     * @return 创建订单请求对象
     */
    private OrderController.CreateOrderRequest request(String itineraryId, String idempotencyKey) {
        return new OrderController.CreateOrderRequest(
                itineraryId,
                idempotencyKey,
                List.of(new OrderController.OrderItemRequest(
                        "r-1", "s-1", LocalDate.of(2026, 11, 1), 1, 100L)));
    }

    /**
     * 测试支付：BOOKED 订单确认支付后推进到 PAID。
     */
    @Test
    void payMovesBookedOrderToPaid() {
        Order booked = new Order();
        booked.setId("order-1");
        booked.setStatus(OrderStatus.BOOKED);
        booked.setTotalAmount(100L);
        booked.setPaymentId("pay-1");
        when(orderRepository.findByIdAndUserId("order-1", "user-1")).thenReturn(Optional.of(booked));
        when(paymentClient.confirm("pay-1")).thenReturn(new PaymentClient.PaymentApiResponse(
                0, "OK", new PaymentClient.PaymentData(
                        "pay-1", "order-1", "user-1", 100L, "SUCCEEDED", "MOCK", Instant.now())));

        Order result = service.pay("user-1", "order-1");

        assertEquals(OrderStatus.PAID, result.getStatus());
    }

    /**
     * 测试取消已支付订单触发退款。
     */
    @Test
    void cancelPaidOrderTriggersRefund() {
        Order paid = new Order();
        paid.setId("order-1");
        paid.setStatus(OrderStatus.PAID);
        paid.setTotalAmount(100L);
        paid.setPaymentId("pay-1");
        when(orderRepository.findByIdAndUserId("order-1", "user-1")).thenReturn(Optional.of(paid));
        when(itemRepository.findByOrderId("order-1")).thenReturn(List.of());
        when(paymentClient.refund(any())).thenReturn(new PaymentClient.RefundApiResponse(
                0, "OK", new PaymentClient.RefundData("refund-1", "SUCCEEDED")));

        Order result = service.cancel("user-1", "order-1");

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
        assertEquals("refund-1", result.getRefundId());
        verify(paymentClient).refund(any());
    }

    /**
     * 测试退款事实源收敛：已退款但本地取消失败的订单，被强制收敛为 CANCELLED。
     */
    @Test
    void convergeToCancelledForcesCancelledWhenRefundExists() {
        Order paid = new Order();
        paid.setId("order-1");
        paid.setStatus(OrderStatus.PAID);
        paid.setPaymentId("pay-1");
        paid.setRefundId("refund-1");
        when(itemRepository.findByOrderId("order-1")).thenReturn(List.of());

        service.convergeToCancelled(paid);

        assertEquals(OrderStatus.CANCELLED, paid.getStatus());
        verify(orchestrator).cancel(any(), any());
    }
}
