package com.aniketh.retail.payment;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
class PaymentService {
    private final PaymentRepository payments;
    private final OrderClient orders;
    private final MockPaymentProvider provider;

    PaymentService(PaymentRepository payments, OrderClient orders, MockPaymentProvider provider) {
        this.payments = payments;
        this.orders = orders;
        this.provider = provider;
    }

    @Transactional
    public Initiation initiate(String key, InitiatePaymentRequest request) {
        payments.lockInitiation(key, request.orderId());
        var existing = payments.findByKey(key);
        if (existing.isPresent()) {
            Payment payment = existing.get();
            if (!payment.orderId().equals(request.orderId()) || !payment.currency().equals(request.currency())
                    || payment.amount().compareTo(request.amount()) != 0) {
                throw conflict("Idempotency key was already used with a different payment request.");
            }
            return new Initiation(payment, false);
        }
        if (payments.hasActiveAttempt(request.orderId())) {
            throw conflict("The order already has a pending or successful payment.");
        }

        var order = orders.get(request.orderId());
        if (!order.status().equals("PENDING_PAYMENT")) {
            throw conflict("The order is not awaiting payment.");
        }
        if (!order.currency().equals(request.currency()) || order.totalAmount().compareTo(request.amount()) != 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Amount and currency must match the order.");
        }
        return new Initiation(payments.insert(key, request), true);
    }

    public Payment get(UUID id) {
        return payments.find(id).orElseThrow(PaymentService::notFound);
    }

    public List<Payment> forOrder(UUID orderId) {
        return payments.findByOrder(orderId);
    }

    @Transactional
    public Payment simulate(UUID id, MockPaymentProvider.Outcome outcome) {
        Payment payment = payments.lockPayment(id).orElseThrow(PaymentService::notFound);
        if (!MockPaymentProvider.NAME.equals(payment.provider())) {
            throw conflict("Only mock payments can be simulated.");
        }
        if (payment.status().name().equals(outcome.name())) {
            return payment;
        }
        if (payment.status() != PaymentStatus.PENDING) {
            throw conflict("The payment already has a different final status.");
        }

        PaymentTransaction transaction = provider.complete(payment, outcome);
        payments.append(transaction);
        return payments.complete(transaction);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment was not found.");
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    record Initiation(Payment payment, boolean created) {
    }
}
