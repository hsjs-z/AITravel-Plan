package com.travelplatform.bookingorder.api;

import com.travelplatform.bookingorder.application.OrderService;
import com.travelplatform.bookingorder.domain.Order;
import com.travelplatform.bookingorder.domain.OrderItem;
import com.travelplatform.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/orders")
    public ApiResponse<OrderResponse> create(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.success(toResponse(orderService.create(userId, request)));
    }

    @GetMapping("/orders")
    public ApiResponse<List<OrderResponse>> list(@RequestHeader("X-User-Id") String userId) {
        List<OrderResponse> orders = orderService.list(userId).stream().map(this::toResponse).toList();
        return ApiResponse.success(orders);
    }

    @GetMapping("/orders/{orderId}")
    public ApiResponse<OrderResponse> get(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("orderId") String orderId) {
        return ApiResponse.success(toResponse(orderService.get(userId, orderId)));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ApiResponse<OrderResponse> cancel(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("orderId") String orderId) {
        return ApiResponse.success(toResponse(orderService.cancel(userId, orderId)));
    }

    @PostMapping("/orders/{orderId}/pay")
    public ApiResponse<OrderResponse> pay(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable("orderId") String orderId) {
        return ApiResponse.success(toResponse(orderService.pay(userId, orderId)));
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = orderService.items(order.getId()).stream()
                .map(i -> new OrderItemResponse(
                        i.getId(), i.getResourceId(), i.getSpecId(), i.getDate(),
                        i.getQuantity(), i.getUnitPrice(), i.getStatus().name(), i.getLockId()))
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getItineraryId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getFailureReason(),
                items);
    }

    public record CreateOrderRequest(
            @NotBlank String itineraryId,
            String idempotencyKey,
            @NotEmpty List<@Valid OrderItemRequest> items) {
    }

    public record OrderItemRequest(
            @NotBlank String resourceId,
            @NotBlank String specId,
            @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @NotNull @Min(1) Integer quantity,
            @NotNull @Positive Long unitPrice) {
    }

    public record OrderResponse(
            String id,
            String itineraryId,
            String status,
            Long totalAmount,
            String failureReason,
            List<OrderItemResponse> items) {
    }

    public record OrderItemResponse(
            String id,
            String resourceId,
            String specId,
            LocalDate date,
            Integer quantity,
            Long unitPrice,
            String status,
            String lockId) {
    }
}
