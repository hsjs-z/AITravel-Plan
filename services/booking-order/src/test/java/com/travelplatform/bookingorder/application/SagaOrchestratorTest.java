package com.travelplatform.bookingorder.application;

import com.travelplatform.bookingorder.domain.BookingSettlementLogRepository;
import com.travelplatform.bookingorder.domain.Order;
import com.travelplatform.bookingorder.domain.OrderItem;
import com.travelplatform.bookingorder.domain.OrderItemRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SagaOrchestratorTest类用于测试SagaOrchestrator类的功能，特别是测试部分失败情况下的补偿机制
 */
class SagaOrchestratorTest {

    // 创建模拟的库存客户端，用于模拟与库存系统的交互
    private final InventoryClient inventoryClient = mock(InventoryClient.class);
    // 创建模拟的行程客户端，用于模拟与行程系统的交互
    private final ItineraryClient itineraryClient = mock(ItineraryClient.class);
    // 创建模拟的预订结算日志仓库，用于模拟数据库操作
    private final BookingSettlementLogRepository logRepository = mock(BookingSettlementLogRepository.class);
    // 创建模拟的订单明细仓库，用于补偿重试时回写释放状态
    private final OrderItemRepository itemRepository = mock(OrderItemRepository.class);

    // 创建SagaOrchestrator实例，使用上述模拟的客户端进行测试
    private final SagaOrchestrator orchestrator =
            new SagaOrchestrator(inventoryClient, itineraryClient, logRepository, itemRepository);

    /**
     * 测试部分失败情况下，已锁定项目的补偿机制
     * 验证当部分订单项预订失败时，系统能正确释放已锁定的资源
     */
    @Test
    void partialFailureCompensatesAlreadyLockedItems() {
        // 创建测试订单
        Order order = new Order();
        order.setId("order-1");
        order.setItineraryId("itinerary-1");
        order.setUserId("user-1");

        // 创建两个订单项
        OrderItem first = item("r-1", 1);
        OrderItem second = item("r-2", 1);

        // 设置库存客户端的模拟行为：当锁定r-1时返回成功，锁定r-2时抛出异常
        when(inventoryClient.lock(any())).thenAnswer(invocation -> {
            InventoryClient.InventoryLockRequest request = invocation.getArgument(0);
            if ("r-1".equals(request.resourceId())) {
                return new InventoryClient.InventoryLockApiResponse(
                        0, "OK", new InventoryClient.InventoryLockData("lock-1"));
            }
            throw new RuntimeException("sold out");
        });

        // 执行预订操作
        boolean result = orchestrator.book(order, List.of(first, second));



        // 验证预订结果
        assertFalse(result); // 预订应该失败
        assertEquals(OrderItemStatus.RELEASED, first.getStatus()); // 补偿后第一个项目应被释放
        assertEquals("lock-1", first.getLockId()); // 第一个项目应该有锁ID
        assertEquals(OrderItemStatus.FAILED, second.getStatus()); // 第二个项目应该失败



        // 验证系统正确释放了资源
        verify(inventoryClient).release("lock-1"); // 验证释放了第一个项目的锁
        verify(itineraryClient).release("itinerary-1", "user-1"); // 验证释放了行程
    }

    /**
     * 创建测试用的订单项
     * @param resourceId 资源ID
     * @param 数量
     * @return 配置好的OrderItem对象
     */
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

    /**
     * 测试补偿重试：对仍处于 BOOKED 且带 lockId 的明细重新释放库存。
     */
    @Test
    void retryCompensationReleasesStillLockedItem() {
        Order order = new Order();
        order.setId("order-1");
        order.setItineraryId("itinerary-1");
        order.setUserId("user-1");

        OrderItem item = item("r-1", 1);
        item.setStatus(OrderItemStatus.BOOKED);
        item.setLockId("lock-1");

        orchestrator.retryCompensation(order, item);

        verify(inventoryClient).release("lock-1");
        assertEquals(OrderItemStatus.RELEASED, item.getStatus());
    }

    /**
     * 取消补偿：已 BOOKED 的明细释放一次并落到 RELEASED；
     * 已 RELEASED 的明细不重复释放（research §13 的枚举回归守卫，FR-017）。
     */
    @Test
    void cancelReleasesBookedItemsOnceAndSkipsAlreadyReleasedOnes() {
        Order order = new Order();
        order.setId("order-1");
        order.setItineraryId("itinerary-1");
        order.setUserId("user-1");

        OrderItem booked = item("r-1", 1);
        booked.setStatus(OrderItemStatus.BOOKED);
        booked.setLockId("lock-1");

        OrderItem alreadyReleased = item("r-2", 1);
        alreadyReleased.setStatus(OrderItemStatus.RELEASED);
        alreadyReleased.setLockId("lock-2");

        orchestrator.cancel(order, List.of(booked, alreadyReleased));

        verify(inventoryClient).release("lock-1");
        verify(inventoryClient, never()).release("lock-2");
        assertEquals(OrderItemStatus.RELEASED, booked.getStatus());
    }
}
