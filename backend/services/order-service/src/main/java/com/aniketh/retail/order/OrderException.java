package com.aniketh.retail.order;

import org.springframework.http.HttpStatus;

import java.util.UUID;

class OrderException extends RuntimeException {

    final HttpStatus status;

    private OrderException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    static OrderException notFound(UUID id) {
        return new OrderException(HttpStatus.NOT_FOUND, "Order " + id + " was not found.");
    }

    static OrderException conflict() {
        return new OrderException(HttpStatus.CONFLICT,
                "This idempotency key was already used with a different order request.");
    }

    static OrderException invalid(String message) {
        return new OrderException(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    static OrderException badRequest(String message) {
        return new OrderException(HttpStatus.BAD_REQUEST, message);
    }

    static OrderException invalidTransition(OrderStatus current, OrderStatus requested) {
        return new OrderException(HttpStatus.CONFLICT,
                "Order status cannot transition from " + current + " to " + requested + ".");
    }
}
