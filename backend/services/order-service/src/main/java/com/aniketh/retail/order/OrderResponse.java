package com.aniketh.retail.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id, String orderNumber, UUID customerId, OrderStatus status, String currency,
        BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxAmount,
        BigDecimal shippingAmount, BigDecimal totalAmount, Instant createdAt, Instant updatedAt,
        List<Item> items
) {
    static OrderResponse from(Order order, List<OrderItem> items) {
        return new OrderResponse(order.id, order.orderNumber, order.customerId, order.status,
                order.currency, order.subtotal, order.discountAmount, order.taxAmount,
                order.shippingAmount, order.totalAmount, order.createdAt, order.updatedAt,
                items.stream().map(Item::from).toList());
    }

    public record Item(
            UUID id, int lineNumber, String sku, String productName, int quantity,
            BigDecimal unitPrice, BigDecimal discountAmount, BigDecimal taxAmount, BigDecimal lineTotal
    ) {
        static Item from(OrderItem item) {
            return new Item(item.id, item.lineNumber, item.sku, item.productName, item.quantity,
                    item.unitPrice, item.discountAmount, item.taxAmount, item.lineTotal);
        }
    }
}
