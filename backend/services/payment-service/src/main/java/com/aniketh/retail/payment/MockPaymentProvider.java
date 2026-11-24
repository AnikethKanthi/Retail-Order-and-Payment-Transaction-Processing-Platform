package com.aniketh.retail.payment;

import org.springframework.stereotype.Component;

@Component
class MockPaymentProvider {
    static final String NAME = "MOCK";

    enum Outcome {
        SUCCESS, FAILED
    }

    PaymentTransaction complete(Payment payment, Outcome outcome) {
        return new PaymentTransaction(payment.id(), "mock-" + payment.id(),
                PaymentStatus.valueOf(outcome.name()), payment.amount(), payment.currency());
    }
}
