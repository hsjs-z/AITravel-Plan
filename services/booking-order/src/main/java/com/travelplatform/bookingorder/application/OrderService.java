package com.travelplatform.bookingorder.application;

import com.travelplatform.bookingorder.api.OrderController;
import com.travelplatform.bookingorder.domain.Order;
import com.travelplatform.bookingorder.domain.OrderItem;
import com.travelplatform.bookingorder.domain.OrderItemRepository;
import com.travelplatform.bookingorder.domain.OrderItemStatus;
import com.travelplatform.bookingorder.domain.OrderRepository;
import com.travelplatform.bookingorder.domain.OrderStateMachine;
import com.travelplatform.bookingorder.domain.OrderStatus;
import com.travelplatform.bookingorder.infrastructure.PaymentClient;
import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;
import com.travelplatform.common.idempotency.IdempotencyKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository itemRepository;
    private final SagaOrchestrator orchestrator;
    private final PaymentClient paymentClient;
    private final OrderEventPublisher events;
    private final OrderStateMachine stateMachine = new OrderStateMachine();

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository itemRepository,
                        SagaOrchestrator orchestrator,
                        PaymentClient paymentClient,
                        OrderEventPublisher events) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.orchestrator = orchestrator;
        this.paymentClient = paymentClient;
        this.events = events;
    }

    @Transactional
    public Order create(String userId, OrderController.CreateOrderRequest request) {
        String idempotencyKey = request.idempotencyKey() != null
                ? request.idempotencyKey()
                : deriveIdempotencyKey(request);

        Order existing = orderRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            return existing;
        }

        Order order = new Order();
        order.setId(UUID.randomUUID().toString());
        order.setUserId(userId);
        order.setItineraryId(request.itineraryId());
        order.setIdempotencyKey(idempotencyKey);
        order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(totalAmount(request));
        order.setCreatedAt(Instant.now());
        try {
            orderRepository.saveAndFlush(order);
        } catch (DataIntegrityViolationException e) {
            return orderRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
        }

        List<OrderItem> items = request.items().stream()
                .map(item -> toItem(order.getId(), item))
                .toList();
        itemRepository.saveAll(items);
        events.orderCreated(order, items.size());

        boolean booked = orchestrator.book(order, items);
        itemRepository.saveAll(items);
        if (booked) {
            stateMachine.booked(order);
            createPayment(order);
            orderRepository.save(order);
            events.orderBooked(order, items);
        } else {
            stateMachine.fail(order, "joint booking failed; already-held resources were released");
            orderRepository.save(order);
            events.orderFailed(order, failedItems(items));
        }
        return order;
    }

    public Order get(String userId, String orderId) {
        return orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "order not found"));
    }

    public List<Order> list(String userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public List<OrderItem> items(String orderId) {
        return itemRepository.findByOrderId(orderId);
    }

    @Transactional
    public Order cancel(String userId, String orderId) {
        Order order = get(userId, orderId);
        if (order.getStatus() == OrderStatus.PAID
                && order.getPaymentId() != null
                && order.getRefundId() == null) {
            PaymentClient.RefundApiResponse refund = paymentClient.refund(
                    new PaymentClient.RefundRequest(
                            order.getPaymentId(), orderId, order.getTotalAmount(), "refund:" + orderId));
            order.setRefundId(refund.data().id());
        }
        stateMachine.cancel(order);
        List<OrderItem> items = itemRepository.findByOrderId(orderId);
        orchestrator.cancel(order, items);
        orderRepository.save(order);
        events.orderCancelled(order);
        return order;
    }

    /**
     * Reconciles an order whose refund already succeeded but whose local cancel
     * may have failed: the refund record is the source of truth, so the order
     * is forced to CANCELLED and its inventory released (idempotently).
     */
    @Transactional
    public void convergeToCancelled(Order order) {
        boolean wasCancelled = order.getStatus() == OrderStatus.CANCELLED;
        if (order.getStatus() != OrderStatus.CANCELLED) {
            order.setStatus(OrderStatus.CANCELLED);
        }
        List<OrderItem> items = itemRepository.findByOrderId(order.getId());
        orchestrator.cancel(order, items);
        orderRepository.save(order);
        if (!wasCancelled) {
            events.orderCancelled(order);
        }
    }

    public boolean hasSucceededRefund(String orderId) {
        try {
            PaymentClient.RefundListApiResponse response = paymentClient.refundsByOrder(orderId);
            return response != null && response.data() != null
                    && response.data().stream().anyMatch(r -> "SUCCEEDED".equals(r.status()));
        } catch (Exception e) {
            log.warn("failed to query refunds for order {}: {}", orderId, e.toString());
            return false;
        }
    }

    @Transactional
    public Order pay(String userId, String orderId) {
        Order order = get(userId, orderId);
        if (order.getStatus() != OrderStatus.BOOKED) {
            throw new BusinessException(ErrorCode.CONFLICT, "only booked orders can be paid");
        }
        if (order.getPaymentId() == null) {
            createPayment(order);
        }
        PaymentClient.PaymentApiResponse response = paymentClient.confirm(order.getPaymentId());
        boolean paid = "SUCCEEDED".equals(response.data().status());
        if (paid) {
            stateMachine.paid(order);
        }
        orderRepository.save(order);
        if (paid) {
            events.orderPaid(order);
        }
        return order;
    }

    @Transactional
    public Order autoConfirm(Order order) {
        stateMachine.confirmed(order);
        orderRepository.save(order);
        events.orderConfirmed(order);
        return order;
    }

    @Transactional
    public void cancelExpired(Order order) {
        stateMachine.cancel(order);
        List<OrderItem> items = itemRepository.findByOrderId(order.getId());
        orchestrator.cancel(order, items);
        orderRepository.save(order);
        events.orderCancelled(order);
    }

    private List<OrderItem> failedItems(List<OrderItem> items) {
        return items.stream()
                .filter(item -> item.getStatus() == OrderItemStatus.FAILED)
                .toList();
    }

    private String deriveIdempotencyKey(OrderController.CreateOrderRequest request) {
        String items = request.items().stream()
                .map(i -> i.resourceId() + ":" + i.specId() + ":" + i.date() + ":" + i.quantity())
                .sorted()
                .collect(Collectors.joining("|"));
        return IdempotencyKeys.of(request.itineraryId(), items);
    }

    private long totalAmount(OrderController.CreateOrderRequest request) {
        return request.items().stream()
                .mapToLong(i -> i.unitPrice() * i.quantity())
                .sum();
    }

    private OrderItem toItem(String orderId, OrderController.OrderItemRequest request) {
        OrderItem item = new OrderItem();
        item.setId(UUID.randomUUID().toString());
        item.setOrderId(orderId);
        item.setResourceId(request.resourceId());
        item.setSpecId(request.specId());
        item.setDate(request.date());
        item.setQuantity(request.quantity());
        item.setUnitPrice(request.unitPrice());
        item.setStatus(OrderItemStatus.PENDING);
        return item;
    }

    private void createPayment(Order order) {
        try {
            PaymentClient.PaymentApiResponse response = paymentClient.create(
                    new PaymentClient.CreatePaymentRequest(
                            order.getId(), order.getUserId(),
                            order.getTotalAmount(), "order:" + order.getId()));
            order.setPaymentId(response.data().id());
        } catch (Exception e) {
            log.warn("failed to create payment for order {}: {}", order.getId(), e.toString());
        }
    }
}
