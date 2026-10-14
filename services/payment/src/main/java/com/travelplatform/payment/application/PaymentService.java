package com.travelplatform.payment.application;

import com.travelplatform.common.api.BusinessException;
import com.travelplatform.common.api.ErrorCode;
import com.travelplatform.payment.domain.CallbackRecord;
import com.travelplatform.payment.domain.CallbackRecordRepository;
import com.travelplatform.payment.domain.Payment;
import com.travelplatform.payment.domain.PaymentRepository;
import com.travelplatform.payment.domain.PaymentStatus;
import com.travelplatform.payment.domain.Refund;
import com.travelplatform.payment.domain.RefundRepository;
import com.travelplatform.payment.domain.RefundStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final CallbackRecordRepository callbackRepository;
    private final PaymentGateway gateway;

    public PaymentService(PaymentRepository paymentRepository,
                          RefundRepository refundRepository,
                          CallbackRecordRepository callbackRepository,
                          PaymentGateway gateway) {
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.callbackRepository = callbackRepository;
        this.gateway = gateway;
    }

    @Transactional
    public Payment create(String orderId, Long amount, String idempotencyKey) {
        Payment existing = paymentRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            return existing;
        }
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID().toString());
        payment.setOrderId(orderId);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setChannel(gateway.name());
        payment.setCreatedAt(Instant.now());
        return paymentRepository.save(payment);
    }

    public Payment get(String paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "payment not found"));
    }

    @Transactional
    public Payment confirm(String paymentId) {
        Payment payment = get(paymentId);
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return payment;
        }
        if (payment.getStatus() == PaymentStatus.PENDING) {
            boolean success = gateway.pay(payment);
            payment.setStatus(success ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED);
            if (success) {
                payment.setPaidAt(Instant.now());
            }
            paymentRepository.save(payment);
        }
        return payment;
    }

    @Transactional
    public Payment reconcileCallback(String channelRef, Long amount, String status) {
        Payment payment = paymentRepository.findById(channelRef)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "payment not found"));

        boolean reconciled = amount != null && amount.equals(payment.getAmount());
        CallbackRecord record = new CallbackRecord();
        record.setId(UUID.randomUUID().toString());
        record.setPaymentId(payment.getId());
        record.setChannelRef(channelRef);
        record.setReconciled(reconciled);
        record.setReceivedAt(Instant.now());
        callbackRepository.save(record);

        if (reconciled && "SUCCESS".equalsIgnoreCase(status)) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            if (payment.getPaidAt() == null) {
                payment.setPaidAt(Instant.now());
            }
            paymentRepository.save(payment);
        }
        return payment;
    }

    @Transactional
    public Refund refund(String paymentId, String orderId, Long amount, String idempotencyKey) {
        Payment payment = get(paymentId);
        if (amount > payment.getAmount()) {
            throw new BusinessException(ErrorCode.CONFLICT, "refund amount exceeds paid amount");
        }
        Refund existing = refundRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            return existing;
        }
        Refund refund = new Refund();
        refund.setId(UUID.randomUUID().toString());
        refund.setPaymentId(paymentId);
        refund.setOrderId(orderId);
        refund.setIdempotencyKey(idempotencyKey);
        refund.setAmount(amount);
        refund.setStatus(RefundStatus.PENDING);
        refund.setCreatedAt(Instant.now());
        refundRepository.save(refund);
        boolean success = gateway.refund(refund);
        refund.setStatus(success ? RefundStatus.SUCCEEDED : RefundStatus.FAILED);
        return refundRepository.save(refund);
    }
}
