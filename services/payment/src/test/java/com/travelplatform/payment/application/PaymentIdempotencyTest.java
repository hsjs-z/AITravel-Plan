package com.travelplatform.payment.application;

import com.travelplatform.common.api.BusinessException;
import com.travelplatform.payment.domain.CallbackRecordRepository;
import com.travelplatform.payment.domain.Payment;
import com.travelplatform.payment.domain.PaymentRepository;
import com.travelplatform.payment.domain.PaymentStatus;
import com.travelplatform.payment.domain.Refund;
import com.travelplatform.payment.domain.RefundRepository;
import com.travelplatform.payment.domain.RefundStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentIdempotencyTest {

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final RefundRepository refundRepository = mock(RefundRepository.class);
    private final CallbackRecordRepository callbackRepository = mock(CallbackRecordRepository.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);

    private final PaymentService service =
            new PaymentService(paymentRepository, refundRepository, callbackRepository, gateway);

    @Test
    void createIsIdempotentByKey() {
        Payment existing = new Payment();
        existing.setId("p-1");
        when(paymentRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        Payment result = service.create("order-1", 100L, "key-1");

        assertEquals("p-1", result.getId());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void confirmIsIdempotentAndChargesOnce() {
        Payment pending = new Payment();
        pending.setId("p-1");
        pending.setStatus(PaymentStatus.PENDING);
        pending.setAmount(100L);
        pending.setCreatedAt(Instant.now());
        when(paymentRepository.findById("p-1")).thenReturn(Optional.of(pending));
        when(gateway.pay(any())).thenReturn(true);

        service.confirm("p-1");
        Payment again = service.confirm("p-1");

        assertEquals(PaymentStatus.SUCCEEDED, again.getStatus());
        verify(gateway, times(1)).pay(any());
    }

    @Test
    void refundIsIdempotentAndRefundsOnce() {
        Payment paid = new Payment();
        paid.setId("p-1");
        paid.setAmount(100L);
        when(paymentRepository.findById("p-1")).thenReturn(Optional.of(paid));
        Refund existing = new Refund();
        existing.setId("r-1");
        existing.setStatus(RefundStatus.SUCCEEDED);
        when(refundRepository.findByIdempotencyKey("rk-1")).thenReturn(Optional.of(existing));

        Refund result = service.refund("p-1", "order-1", 100L, "rk-1");

        assertEquals("r-1", result.getId());
        verify(gateway, never()).refund(any());
    }

    @Test
    void refundExceedingPaidAmountIsRejected() {
        Payment paid = new Payment();
        paid.setId("p-1");
        paid.setAmount(100L);
        when(paymentRepository.findById("p-1")).thenReturn(Optional.of(paid));

        assertThrows(BusinessException.class, () -> service.refund("p-1", "order-1", 101L, "rk-1"));
    }

    @Test
    void callbackWithMismatchedAmountIsNotReconciled() {
        Payment pending = new Payment();
        pending.setId("p-1");
        pending.setAmount(100L);
        pending.setStatus(PaymentStatus.PENDING);
        when(paymentRepository.findById("p-1")).thenReturn(Optional.of(pending));

        Payment result = service.reconcileCallback("p-1", 99L, "SUCCESS");

        assertEquals(PaymentStatus.PENDING, result.getStatus());
        verify(paymentRepository, never()).save(any());
    }
}
