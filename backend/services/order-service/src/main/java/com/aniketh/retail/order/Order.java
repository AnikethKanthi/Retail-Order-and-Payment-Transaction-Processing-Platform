package com.aniketh.retail.order;

import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SourceType;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders", schema = "order_service")
class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @Column(nullable = false, length = 40)
    String orderNumber;

    @Column(nullable = false)
    UUID customerId;

    @Column(nullable = false, length = 128)
    String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(nullable = false, length = 3)
    String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    BigDecimal shippingAmount;

    @Generated
    @Column(precision = 19, scale = 4)
    BigDecimal totalAmount;

    @Generated
    @Column(nullable = false)
    Instant createdAt;

    @UpdateTimestamp(source = SourceType.DB)
    @Column(nullable = false)
    Instant updatedAt;

    @Version
    Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    @OrderBy("lineNumber ASC")
    List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    Order(UUID customerId, String idempotencyKey, String currency, BigDecimal shippingAmount) {
        this.orderNumber = "ORD-" + UUID.randomUUID();
        this.customerId = customerId;
        this.idempotencyKey = idempotencyKey;
        this.currency = currency;
        this.shippingAmount = shippingAmount;
    }

    void addItem(OrderItem item) {
        items.add(item);
        subtotal = subtotal.add(item.grossAmount());
        discountAmount = discountAmount.add(item.discountAmount);
        taxAmount = taxAmount.add(item.taxAmount);
    }

    void transitionTo(OrderStatus nextStatus) {
        if (status == nextStatus && status != OrderStatus.FULFILLED) {
            return;
        }
        if (status != OrderStatus.PENDING_PAYMENT || nextStatus == OrderStatus.FULFILLED) {
            throw OrderException.invalidTransition(status, nextStatus);
        }
        status = nextStatus;
    }
}
