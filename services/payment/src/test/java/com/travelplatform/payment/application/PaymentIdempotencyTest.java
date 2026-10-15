package com.travelplatform.payment.application;

import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.outbox.OutboxRecorder;
import com.travelplatform.common.outbox.OutboxStore;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * 支付幂等性测试类
 * 测试支付创建、确认、退款等操作的幂等性处理
 */
class PaymentIdempotencyTest {

    // 使用mock创建支付仓库对象
    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    // 使用mock创建退款仓库对象
    private final RefundRepository refundRepository = mock(RefundRepository.class);
    // 使用mock创建回调记录仓库对象
    private final CallbackRecordRepository callbackRepository = mock(CallbackRecordRepository.class);
    // 使用mock创建支付网关对象
    private final PaymentGateway gateway = mock(PaymentGateway.class);

    private final PaymentEventPublisher events =
            new PaymentEventPublisher(new OutboxRecorder(
                    mock(OutboxStore.class), new ObjectMapper().findAndRegisterModules()));

    // 创建支付服务实例，注入所有依赖的仓库和网关
    private final PaymentService service =
            new PaymentService(paymentRepository, refundRepository, callbackRepository, gateway, events);

    /**
     * 测试支付创建操作的幂等性
     * 验证使用相同的幂等键创建支付时，不会重复创建
     */
    @Test
    void createIsIdempotentByKey() {
        // 创建已存在的支付对象
        Payment existing = new Payment();
        existing.setId("p-1");
        // 设置mock行为，当查询幂等键"key-1"时返回已存在的支付
        when(paymentRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        // 尝试创建支付
        Payment result = service.create("order-1", "user-1", 100L, "key-1");

        // 验证返回的支付ID与已存在的支付相同
        assertEquals("p-1", result.getId());
        // 验证没有调用save方法，即没有重复保存
        verify(paymentRepository, never()).save(any());
    }

    /**
     * 测试支付确认操作的幂等性
     * 验证对同一笔待支付支付进行多次确认时，只会实际扣费一次
     */
    @Test
    void confirmIsIdempotentAndChargesOnce() {
        // 创建待支付状态的支付对象
        Payment pending = new Payment();
        pending.setId("p-1");
        pending.setStatus(PaymentStatus.PENDING);
        pending.setAmount(100L);
        pending.setCreatedAt(Instant.now());
        // 设置mock行为，当查询支付ID时返回待支付状态
        when(paymentRepository.findById("p-1")).thenReturn(Optional.of(pending));
        // 设置mock行为，支付网关支付操作返回成功
        when(gateway.pay(any())).thenReturn(true);

        // 进行两次支付确认
        service.confirm("p-1");
        Payment again = service.confirm("p-1");

        // 验证最终状态为成功
        assertEquals(PaymentStatus.SUCCEEDED, again.getStatus());
        // 验证支付网关的pay方法只被调用一次
        verify(gateway, times(1)).pay(any());
    }

    /**
     * 测试退款操作的幂等性
     * 验证对同一笔退款使用相同的幂等键时，不会重复退款
     */
    @Test
    void refundIsIdempotentAndRefundsOnce() {
        // 创建已支付状态的支付对象
        Payment paid = new Payment();
        paid.setId("p-1");
        paid.setAmount(100L);
        // 设置mock行为，当查询支付ID时返回已支付状态
        when(paymentRepository.findById("p-1")).thenReturn(Optional.of(paid));
        // 创建已成功的退款对象
        Refund existing = new Refund();
        existing.setId("r-1");
        existing.setStatus(RefundStatus.SUCCEEDED);
        // 设置mock行为，当查询退款幂等键时返回已存在的退款
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

    /**
     * 终态守卫（research §12）：对已成功的支付重复收到成功回调不得翻状态、不得重复落库。
     */
    @Test
    void repeatedSuccessCallbackOnSucceededPaymentKeepsTerminalState() {
        when(paymentRepository.findById("p-1"))
                .thenReturn(Optional.of(payment("p-1", 100L, PaymentStatus.SUCCEEDED)));

        Payment result = service.reconcileCallback("p-1", 100L, "SUCCESS");

        assertEquals(PaymentStatus.SUCCEEDED, result.getStatus());
        verify(paymentRepository, never()).save(any());
    }

    /**
     * 乱序回调（先成功、后失败）不得把已成功支付翻成失败（FR-030/FR-035）。
     */
    @Test
    void lateFailureCallbackAfterSuccessDoesNotFlipState() {
        when(paymentRepository.findById("p-1"))
                .thenReturn(Optional.of(payment("p-1", 100L, PaymentStatus.SUCCEEDED)));

        Payment result = service.reconcileCallback("p-1", 100L, "FAILED");

        assertEquals(PaymentStatus.SUCCEEDED, result.getStatus());
        verify(paymentRepository, never()).save(any());
    }

    private Payment payment(String id, long amount, PaymentStatus status) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setAmount(amount);
        payment.setStatus(status);
        return payment;
    }
}
