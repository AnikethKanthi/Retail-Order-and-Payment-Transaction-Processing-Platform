package com.aniketh.retail.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final UUID CUSTOMER = UUID.randomUUID();

    @Mock OrderRepository orders;
    @Mock OrderItemRepository items;
    @Mock JdbcTemplate jdbc;
    @InjectMocks OrderService service;

    @Test
    void createsPendingOrderWithCalculatedAmountsAndSequentialLines() {
        when(orders.hasActiveCustomer(CUSTOMER)).thenReturn(true);
        when(orders.saveAndFlush(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.create("checkout", request("1.50"));

        assertThat(result.created()).isTrue();
        assertThat(result.order().status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(result.order().subtotal()).isEqualByComparingTo("20.00");
        assertThat(result.order().discountAmount()).isEqualByComparingTo("1.50");
        assertThat(result.order().taxAmount()).isEqualByComparingTo("0.50");
        assertThat(result.order().shippingAmount()).isEqualByComparingTo("2.00");
        assertThat(result.order().items()).extracting(OrderResponse.Item::lineNumber).containsExactly(1, 2);
        assertThat(result.order().items().getFirst().sku()).isEqualTo("SKU-1");
    }

    @Test
    void replaysEquivalentRequestWithoutWritingAndRejectsDifferentPayload() {
        var request = request("1.50");
        when(orders.hasActiveCustomer(CUSTOMER)).thenReturn(true);
        when(orders.saveAndFlush(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.id = UUID.randomUUID();
            when(orders.findByIdempotencyKey("checkout")).thenReturn(Optional.of(order));
            when(items.findByOrder_IdOrderByLineNumberAsc(order.id)).thenReturn(order.items);
            return order;
        });

        var first = service.create("checkout", request);
        var replay = service.create("checkout", request("1.5000"));

        assertThat(replay.created()).isFalse();
        assertThat(replay.order()).isEqualTo(first.order());
        var conflict = assertThrows(OrderException.class, () -> service.create("checkout", request("1.00")));
        assertThat(conflict.status).isEqualTo(HttpStatus.CONFLICT);
        verify(orders, times(1)).saveAndFlush(any(Order.class));
    }

    @Test
    void rejectsInvalidDiscountAndCurrencyPrecisionBeforeDatabaseAccess() {
        var discount = assertThrows(OrderException.class, () -> service.create("a", request("11.00")));
        var precision = assertThrows(OrderException.class, () -> service.create("b", request("0.001")));

        assertThat(discount.status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(precision.status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        verifyNoInteractions(orders, items, jdbc);
    }

    @Test
    void rejectsTotalOverflowBeforeDatabaseAccess() {
        var request = new CreateOrderRequest(CUSTOMER, "USD", new BigDecimal("0.01"), List.of(
                new CreateOrderRequest.Item("MAX", "Maximum price", 1,
                        new BigDecimal("999999999999999.99"), null, null)));

        var error = assertThrows(OrderException.class, () -> service.create("overflow", request));
        assertThat(error.status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(error).hasMessage("An order or line amount exceeds the supported range.");
        verifyNoInteractions(orders, items, jdbc);
    }

    @Test
    void rejectsUnknownOrInactiveCustomer() {
        var error = assertThrows(OrderException.class, () -> service.create("checkout", request("0")));

        assertThat(error.status).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        verify(orders, never()).saveAndFlush(any(Order.class));
    }

    @Test
    void retrievesOrderAndReturnsNotFoundForUnknownId() {
        UUID id = UUID.randomUUID();
        Order order = new Order(CUSTOMER, "checkout", "USD", BigDecimal.ZERO);
        order.id = id;
        when(orders.findById(id)).thenReturn(Optional.of(order));

        assertThat(service.get(id).id()).isEqualTo(id);
        assertThat(assertThrows(OrderException.class, () -> service.get(UUID.randomUUID())).status)
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void onlyPendingOrdersCanTransitionAndSameStatusRequestsCanBeRetried() {
        for (OrderStatus next : List.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.FAILED)) {
            Order order = new Order(CUSTOMER, "checkout", "USD", BigDecimal.ZERO);
            order.transitionTo(next);
            order.transitionTo(next);
            assertThat(order.status).isEqualTo(next);
            assertThat(assertThrows(OrderException.class, () -> order.transitionTo(OrderStatus.PENDING_PAYMENT)).status)
                    .isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Test
    void doesNotAllowTransitionsToOrFromHistoricalFulfilledStatus() {
        Order order = new Order(CUSTOMER, "checkout", "USD", BigDecimal.ZERO);
        assertThrows(OrderException.class, () -> order.transitionTo(OrderStatus.FULFILLED));
        order.status = OrderStatus.FULFILLED;
        assertThrows(OrderException.class, () -> order.transitionTo(OrderStatus.CANCELLED));
    }

    private CreateOrderRequest request(String discount) {
        return new CreateOrderRequest(CUSTOMER, "USD", new BigDecimal("2.00"), List.of(
                new CreateOrderRequest.Item(" SKU-1 ", "First item", 2, new BigDecimal("5.00"),
                        new BigDecimal(discount), new BigDecimal("0.50")),
                new CreateOrderRequest.Item("SKU-2", "Second item", 1, new BigDecimal("10.00"), null, null)));
    }
}
