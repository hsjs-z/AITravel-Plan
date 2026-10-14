package com.travelplatform.payment.application;

import com.travelplatform.payment.domain.Payment;
import com.travelplatform.payment.domain.Refund;
import org.springframework.stereotype.Component;

@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public String name() {
        return "MOCK";
    }

    @Override
    public boolean pay(Payment payment) {
        return true;
    }

    @Override
    public boolean refund(Refund refund) {
        return true;
    }
}
