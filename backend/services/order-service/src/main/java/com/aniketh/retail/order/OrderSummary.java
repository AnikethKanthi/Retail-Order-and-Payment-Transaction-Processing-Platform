package com.aniketh.retail.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderSummary(
        UUID id, String orderNumber, UUID customerId, OrderStatus status, String currency,
        BigDecimal totalAmount, Instant createdAt, Instant updatedAt
) {
    static OrderSummary from(Order order) {
        return new OrderSummary(order.id, order.orderNumber, order.customerId, order.status,
                order.currency, order.totalAmount, order.createdAt, order.updatedAt);
    }
}
