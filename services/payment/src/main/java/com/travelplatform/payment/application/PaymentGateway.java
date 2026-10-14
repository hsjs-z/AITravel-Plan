package com.travelplatform.payment.application;

import com.travelplatform.payment.domain.Payment;
import com.travelplatform.payment.domain.Refund;

/**
 * Port for the payment channel. v1 ships a mock implementation; a real WeChat
 * implementation plugs in here without changing callers.
 */
public interface PaymentGateway {

    String name();

    boolean pay(Payment payment);

    boolean refund(Refund refund);
}
