package com.travelplatform.payment.application;

import com.travelplatform.common.outbox.EventTypes;
import com.travelplatform.common.outbox.OutboxRecorder;
import com.travelplatform.common.outbox.OutboxTopics;
import com.travelplatform.payment.domain.Payment;
import com.travelplatform.payment.domain.Refund;
import org.springframework.stereotype.Component;

/**
 * Publishes payment/refund events through the transactional outbox (T052).
 * The user id is carried in the payload so notification stays a per-user concern.
 */
@Component
public class PaymentEventPublisher {

    private static final String AGGREGATE_TYPE = "PAYMENT";
    private static final String REFUND_AGGREGATE_TYPE = "REFUND";

    private final OutboxRecorder recorder;

    public PaymentEventPublisher(OutboxRecorder recorder) {
        this.recorder = recorder;
    }

    public void paymentSucceeded(Payment payment) {
        recorder.record(OutboxTopics.PAYMENT_EVENTS, EventTypes.PAYMENT_SUCCEEDED, AGGREGATE_TYPE,
                payment.getId(), new PaymentSucceeded(payment.getId(), payment.getOrderId(),
                        payment.getUserId(), payment.getAmount(), payment.getStatus().name()));
    }

    public void paymentFailed(Payment payment) {
        recorder.record(OutboxTopics.PAYMENT_EVENTS, EventTypes.PAYMENT_FAILED, AGGREGATE_TYPE,
                payment.getId(), new PaymentFailed(payment.getId(), payment.getOrderId(),
                        payment.getUserId(), payment.getAmount(), payment.getStatus().name()));
    }

    public void refundSucceeded(Refund refund, Payment payment) {
        recorder.record(OutboxTopics.PAYMENT_EVENTS, EventTypes.REFUND_SUCCEEDED, REFUND_AGGREGATE_TYPE,
                refund.getId(), new RefundSucceeded(refund.getId(), refund.getPaymentId(),
                        refund.getOrderId(), payment == null ? null : payment.getUserId(),
                        refund.getAmount(), refund.getStatus().name()));
    }

    public record PaymentSucceeded(String paymentId, String orderId, String userId, Long amount, String status) {
    }

    public record PaymentFailed(String paymentId, String orderId, String userId, Long amount, String status) {
    }

    public record RefundSucceeded(String refundId, String paymentId, String orderId, String userId,
                                  Long amount, String status) {
    }
}
