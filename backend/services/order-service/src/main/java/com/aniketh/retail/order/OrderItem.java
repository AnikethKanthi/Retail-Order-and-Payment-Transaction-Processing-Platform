package com.aniketh.retail.order;

import jakarta.persistence.*;
import org.hibernate.annotations.Generated;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_items", schema = "order_service")
class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    Order order;

    @Column(nullable = false)
    int lineNumber;

    @Column(nullable = false, length = 64)
    String sku;

    @Column(nullable = false, length = 255)
    String productName;

    @Column(nullable = false)
    int quantity;

    @Column(nullable = false, precision = 19, scale = 4)
    BigDecimal unitPrice;

    @Column(nullable = false, precision = 19, scale = 4)
    BigDecimal discountAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    BigDecimal taxAmount;

    @Generated
    @Column(precision = 19, scale = 4)
    BigDecimal lineTotal;

    @Generated
    @Column(nullable = false)
    Instant createdAt;

    protected OrderItem() {
    }

    OrderItem(Order order, int lineNumber, String sku, String productName, int quantity,
              BigDecimal unitPrice, BigDecimal discountAmount, BigDecimal taxAmount) {
        this.order = order;
        this.lineNumber = lineNumber;
        this.sku = sku;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.discountAmount = discountAmount;
        this.taxAmount = taxAmount;
    }

    BigDecimal grossAmount() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
