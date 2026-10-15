package com.travelplatform.payment.api;

import com.travelplatform.common.api.ApiResponse;
import com.travelplatform.payment.application.PaymentService;
import com.travelplatform.payment.domain.Payment;
import com.travelplatform.payment.domain.Refund;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/payments")
    public ApiResponse<PaymentResponse> create(@Valid @RequestBody CreatePaymentRequest request) {
        Payment payment = paymentService.create(
                request.orderId(), request.userId(), request.amount(), request.idempotencyKey());
        return ApiResponse.success(toResponse(payment));
    }

    @PostMapping("/payments/{paymentId}/confirm")
    public ApiResponse<PaymentResponse> confirm(@PathVariable("paymentId") String paymentId) {
        return ApiResponse.success(toResponse(paymentService.confirm(paymentId)));
    }

    @PostMapping("/payments/callback")
    public ApiResponse<PaymentResponse> callback(@RequestBody CallbackRequest request) {
        Payment payment = paymentService.reconcileCallback(request.channelRef(), request.amount(), request.status());
        return ApiResponse.success(toResponse(payment));
    }

    @GetMapping("/payments/{paymentId}")
    public ApiResponse<PaymentResponse> get(@PathVariable("paymentId") String paymentId) {
        return ApiResponse.success(toResponse(paymentService.get(paymentId)));
    }

    @PostMapping("/refunds")
    public ApiResponse<RefundResponse> refund(@Valid @RequestBody RefundRequest request) {
        Refund refund = paymentService.refund(
                request.paymentId(), request.orderId(), request.amount(), request.idempotencyKey());
        return ApiResponse.success(new RefundResponse(refund.getId(), refund.getStatus().name()));
    }

    @GetMapping("/refunds/order/{orderId}")
    public ApiResponse<List<RefundResponse>> refundsByOrder(@PathVariable("orderId") String orderId) {
        List<RefundResponse> refunds = paymentService.refundsByOrder(orderId).stream()
                .map(r -> new RefundResponse(r.getId(), r.getStatus().name()))
                .toList();
        return ApiResponse.success(refunds);
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getStatus().name(),
                payment.getChannel(),
                payment.getPaidAt());
    }

    public record CreatePaymentRequest(
            @NotBlank String orderId,
            String userId,
            @NotNull @Positive Long amount,
            @NotBlank String idempotencyKey) {
    }

    public record CallbackRequest(String channelRef, Long amount, String status) {
    }

    public record RefundRequest(
            @NotBlank String paymentId,
            @NotBlank String orderId,
            @NotNull @Positive Long amount,
            @NotBlank String idempotencyKey) {
    }

    public record PaymentResponse(
            String id,
            String orderId,
            String userId,
            Long amount,
            String status,
            String channel,
            java.time.Instant paidAt) {
    }

    public record RefundResponse(String id, String status) {
    }
}
