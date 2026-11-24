package com.aniketh.retail.payment;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class PaymentRepository {
    private static final RowMapper<Payment> MAPPER = (row, number) -> readPayment(row);
    private final JdbcClient jdbc;

    PaymentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void lockInitiation(String key, UUID orderId) {
        lock(80701, key);
        lock(80702, orderId.toString());
    }

    private void lock(int namespace, String key) {
        // Separate advisory-lock namespaces keep key locks distinct from order locks.
        jdbc.sql("SELECT 1 FROM pg_advisory_xact_lock(:namespace, hashtext(:key))")
                .param("namespace", namespace).param("key", key).query(Integer.class).single();
    }

    Optional<Payment> findByKey(String key) {
        return jdbc.sql("SELECT * FROM payment_service.payments WHERE idempotency_key = :key")
                .param("key", key).query(MAPPER).optional();
    }

    boolean hasActiveAttempt(UUID orderId) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM payment_service.payments WHERE order_id = :orderId
                    AND status IN ('PENDING', 'AUTHORIZED', 'CAPTURED', 'PARTIALLY_REFUNDED'))
                """).param("orderId", orderId).query(Boolean.class).single();
    }

    Payment insert(String key, InitiatePaymentRequest request) {
        return jdbc.sql("""
                INSERT INTO payment_service.payments (order_id, idempotency_key, provider, amount, currency)
                VALUES (:orderId, :key, :provider, :amount, :currency)
                RETURNING *
                """).param("orderId", request.orderId()).param("key", key)
                .param("provider", MockPaymentProvider.NAME).param("amount", request.amount())
                .param("currency", request.currency()).query(MAPPER).single();
    }

    Optional<Payment> find(UUID id) {
        return jdbc.sql("SELECT * FROM payment_service.payments WHERE id = :id")
                .param("id", id).query(MAPPER).optional();
    }

    Optional<Payment> lockPayment(UUID id) {
        return jdbc.sql("SELECT * FROM payment_service.payments WHERE id = :id FOR UPDATE")
                .param("id", id).query(MAPPER).optional();
    }

    List<Payment> findByOrder(UUID orderId) {
        return jdbc.sql("""
                SELECT * FROM payment_service.payments WHERE order_id = :orderId ORDER BY created_at DESC, id DESC
                """).param("orderId", orderId).query(MAPPER).list();
    }

    Payment complete(PaymentTransaction transaction) {
        boolean success = transaction.outcome() == PaymentStatus.SUCCESS;
        return jdbc.sql("""
                UPDATE payment_service.payments SET status = :status, provider_payment_id = :reference,
                    version = version + 1, updated_at = GREATEST(updated_at, statement_timestamp())
                WHERE id = :id AND status = 'PENDING'
                RETURNING *
                """).param("status", success ? "CAPTURED" : "FAILED")
                .param("reference", transaction.providerReference()).param("id", transaction.paymentId())
                .query(MAPPER).single();
    }

    void append(PaymentTransaction transaction) {
        jdbc.sql("""
                INSERT INTO payment_service.transaction_records
                    (payment_id, idempotency_key, provider_transaction_id, operation, status, amount, currency)
                VALUES (:id, 'mock-completion', :reference, 'CAPTURE', :status, :amount, :currency)
                """).param("id", transaction.paymentId()).param("reference", transaction.providerReference())
                .param("status", transaction.outcome() == PaymentStatus.SUCCESS ? "SUCCEEDED" : "FAILED")
                .param("amount", transaction.amount()).param("currency", transaction.currency()).update();
    }

    private static Payment readPayment(ResultSet row) throws SQLException {
        return new Payment(row.getObject("id", UUID.class), row.getObject("order_id", UUID.class),
                row.getString("provider"), PaymentStatus.fromDatabase(row.getString("status")),
                row.getBigDecimal("amount"), row.getString("currency"),
                row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant());
    }
}
