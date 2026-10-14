package com.travelplatform.bookingorder.infrastructure;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.time.Instant;

@FeignClient(name = "payment")
public interface PaymentClient {

    @PostMapping("/payments")
    PaymentApiResponse create(@RequestBody CreatePaymentRequest request);

    @PostMapping("/payments/{paymentId}/confirm")
    PaymentApiResponse confirm(@PathVariable("paymentId") String paymentId);

    @PostMapping("/refunds")
    RefundApiResponse refund(@RequestBody RefundRequest request);

    record CreatePaymentRequest(String orderId, Long amount, String idempotencyKey) {
    }

    record PaymentApiResponse(int code, String message, PaymentData data) {
    }

    record PaymentData(String id, String orderId, Long amount, String status, String channel, Instant paidAt) {
    }

    record RefundRequest(String paymentId, String orderId, Long amount, String idempotencyKey) {
    }

    record RefundApiResponse(int code, String message, RefundData data) {
    }

    record RefundData(String id, String status) {
    }
}
