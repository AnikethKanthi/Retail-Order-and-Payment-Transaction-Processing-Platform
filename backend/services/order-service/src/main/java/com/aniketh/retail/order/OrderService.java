package com.aniketh.retail.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

@Service
@Validated
class OrderService {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999999.9999");
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final JdbcTemplate jdbc;

    OrderService(OrderRepository orders, OrderItemRepository items, JdbcTemplate jdbc) {
        this.orders = orders;
        this.items = items;
        this.jdbc = jdbc;
    }

    @Transactional
    public Creation create(String key, @NotNull @Valid CreateOrderRequest request) {
        Order candidate = buildOrder(key, request);

        // Serialize concurrent requests for the same key until this transaction commits.
        jdbc.queryForObject("""
                SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(?, 0))
                """, Integer.class, "order-service:" + key);

        var existing = orders.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            Order order = existing.get();
            List<OrderItem> lines = items.findByOrder_IdOrderByLineNumberAsc(order.id);
            if (!matches(order, lines, candidate)) {
                throw OrderException.conflict();
            }
            return new Creation(OrderResponse.from(order, lines), false);
        }

        if (!orders.hasActiveCustomer(request.customerId())) {
            throw OrderException.invalid("customerId must identify an active customer.");
        }

        Order saved = orders.saveAndFlush(candidate);
        return new Creation(OrderResponse.from(saved, saved.items), true);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        Order order = orders.findById(id).orElseThrow(() -> OrderException.notFound(id));
        return OrderResponse.from(order, items.findByOrder_IdOrderByLineNumberAsc(id));
    }

    @Transactional(readOnly = true)
    public Page<OrderSummary> list(Pageable pageable) {
        return orders.findAll(pageable).map(OrderSummary::from);
    }

    @Transactional(readOnly = true)
    public Page<OrderSummary> customerHistory(UUID customerId, Pageable pageable) {
        return orders.findByCustomerId(customerId, pageable).map(OrderSummary::from);
    }

    @Transactional
    public OrderResponse updateStatus(UUID id, OrderStatus status) {
        Order order = orders.findForStatusUpdate(id).orElseThrow(() -> OrderException.notFound(id));
        order.transitionTo(status);
        orders.flush();
        return OrderResponse.from(order, items.findByOrder_IdOrderByLineNumberAsc(id));
    }

    private Order buildOrder(String key, CreateOrderRequest request) {
        int fractionDigits = currencyFractionDigits(request.currency());
        Order order = new Order(request.customerId(), key, request.currency(),
                amount(request.shippingAmount(), fractionDigits));

        for (CreateOrderRequest.Item input : request.items()) {
            OrderItem item = new OrderItem(order, order.items.size() + 1, input.sku().strip(),
                    input.productName().strip(), input.quantity(), amount(input.unitPrice(), fractionDigits),
                    amount(input.discountAmount(), fractionDigits), amount(input.taxAmount(), fractionDigits));
            BigDecimal gross = item.grossAmount();
            if (item.discountAmount.compareTo(gross) > 0) {
                throw OrderException.invalid("Line " + item.lineNumber + " discount exceeds its gross amount.");
            }
            checkRange(item.calculatedTotal(gross));
            order.addItem(item);
        }

        checkRange(order.subtotal);
        checkRange(order.taxAmount);
        checkRange(order.calculatedTotal());
        return order;
    }

    private int currencyFractionDigits(String currency) {
        int fractionDigits;
        try {
            fractionDigits = Currency.getInstance(currency).getDefaultFractionDigits();
        } catch (IllegalArgumentException exception) {
            throw OrderException.invalid("currency must be a recognized ISO 4217 currency.");
        }
        if (fractionDigits < 0 || fractionDigits > 4) {
            throw OrderException.invalid("currency has unsupported minor units.");
        }
        return fractionDigits;
    }

    private BigDecimal amount(BigDecimal input, int fractionDigits) {
        BigDecimal value = input == null ? BigDecimal.ZERO : input;
        if (value.stripTrailingZeros().scale() > fractionDigits) {
            throw OrderException.invalid("Monetary amounts must match the currency's minor units.");
        }
        return value.setScale(4);
    }

    private void checkRange(BigDecimal amount) {
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw OrderException.invalid("An order or line amount exceeds the supported range.");
        }
    }

    private boolean matches(Order saved, List<OrderItem> lines, Order candidate) {
        return saved.customerId.equals(candidate.customerId)
                && saved.currency.equals(candidate.currency)
                && saved.shippingAmount.compareTo(candidate.shippingAmount) == 0
                && lines.size() == candidate.items.size()
                && IntStream.range(0, lines.size())
                        .allMatch(index -> matches(lines.get(index), candidate.items.get(index)));
    }

    private boolean matches(OrderItem saved, OrderItem candidate) {
        return saved.sku.equals(candidate.sku)
                && saved.productName.equals(candidate.productName)
                && saved.quantity == candidate.quantity
                && saved.unitPrice.compareTo(candidate.unitPrice) == 0
                && saved.discountAmount.compareTo(candidate.discountAmount) == 0
                && saved.taxAmount.compareTo(candidate.taxAmount) == 0;
    }

    record Creation(OrderResponse order, boolean created) {
    }
}
