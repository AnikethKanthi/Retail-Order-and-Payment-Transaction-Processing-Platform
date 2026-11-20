package com.aniketh.retail.order;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "ORDER_DB_ROLLBACK_TEST", matches = "true")
class OrderPostgresRollbackTest {

    @Autowired OrderService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void rollsBackOrderAndFirstItemWhenSecondItemInsertFails() {
        UUID customer = UUID.randomUUID();
        String token = customer.toString().replace("-", "");
        String key = "rollback-" + token;
        String firstSku = "first-" + token;
        String secondSku = "second-" + token;
        String constraint = "rollback_" + token;

        var request = new CreateOrderRequest(customer, "USD", new BigDecimal("2.00"), List.of(
                new CreateOrderRequest.Item(firstSku, "First", 2, new BigDecimal("5.00"),
                        new BigDecimal("1.50"), new BigDecimal("0.50")),
                new CreateOrderRequest.Item(secondSku, "Second", 1, new BigDecimal("10.00"), null, null)));

        try {
            jdbc.update("""
                    INSERT INTO order_service.customers (id, email, display_name, status)
                    VALUES (?, ?, 'Rollback fixture', 'INACTIVE')
                    """, customer, token + "@example.test");
            assertThat(assertThrows(OrderException.class, () -> service.create(key, request)))
                    .hasMessage("customerId must identify an active customer.");
            assertThrows(ConstraintViolationException.class, () -> service.create(key,
                    new CreateOrderRequest(customer, "USD", null, List.of())));
            jdbc.update("UPDATE order_service.customers SET status = 'ACTIVE' WHERE id = ?", customer);

            // Both identifiers are generated from a UUID. Only line two of this fixture can fail.
            jdbc.execute("ALTER TABLE order_service.order_items ADD CONSTRAINT " + constraint
                    + " CHECK (line_number <> 2 OR sku <> '" + secondSku + "') NOT VALID");
            var failure = assertThrows(DataIntegrityViolationException.class, () -> service.create(key, request));
            assertThat(failure.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class, cause -> {
                assertThat(cause.getSQLState()).isEqualTo("23514");
                assertThat(cause.getMessage()).contains(constraint);
            });

            assertThat(jdbc.queryForObject("SELECT count(*) FROM order_service.orders WHERE idempotency_key = ?",
                    Long.class, key)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM order_service.order_items WHERE sku IN (?, ?)",
                    Long.class, firstSku, secondSku)).isZero();

            dropConstraint(constraint);
            var created = service.create(key, request);
            assertThat(created.created()).isTrue();
            var order = service.get(created.order().id());
            assertThat(order.subtotal()).isEqualByComparingTo("20.00");
            assertThat(order.discountAmount()).isEqualByComparingTo("1.50");
            assertThat(order.taxAmount()).isEqualByComparingTo("0.50");
            assertThat(order.totalAmount()).isEqualByComparingTo("21.00");
            assertThat(order.items()).extracting(OrderResponse.Item::lineTotal)
                    .containsExactly(new BigDecimal("9.0000"), new BigDecimal("10.0000"));
        } finally {
            dropConstraint(constraint);
            jdbc.update("""
                    DELETE FROM order_service.order_items WHERE order_id IN
                        (SELECT id FROM order_service.orders WHERE idempotency_key = ?)
                    """, key);
            jdbc.update("DELETE FROM order_service.orders WHERE idempotency_key = ?", key);
            jdbc.update("DELETE FROM order_service.customers WHERE id = ?", customer);
        }
    }

    private void dropConstraint(String name) {
        jdbc.execute("ALTER TABLE order_service.order_items DROP CONSTRAINT IF EXISTS " + name);
    }
}
