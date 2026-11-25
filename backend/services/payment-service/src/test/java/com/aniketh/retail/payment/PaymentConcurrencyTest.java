package com.aniketh.retail.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "PAYMENT_DB_CONCURRENCY_TEST", matches = "true")
class PaymentConcurrencyTest {
    @Autowired PaymentService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean OrderClient orders;

    @Test
    void concurrentIdenticalRequestsCreateOnePaymentAndOneTransaction() throws Exception {
        var request = checkout();
        String key = UUID.randomUUID().toString();
        var initiations = race(() -> service.initiate(key, request), () -> service.initiate(key, request));

        assertThat(initiations).extracting(PaymentService.Initiation::created)
                .containsExactlyInAnyOrder(true, false);
        var payment = initiations.getFirst().payment();
        assertThat(initiations).extracting(result -> result.payment().id()).containsOnly(payment.id());
        assertThat(service.forOrder(request.orderId())).hasSize(1);
        verify(orders).get(request.orderId());

        var completions = race(
                () -> service.simulate(payment.id(), MockPaymentProvider.Outcome.SUCCESS),
                () -> service.simulate(payment.id(), MockPaymentProvider.Outcome.SUCCESS));
        assertThat(completions).hasSize(2).allSatisfy(result -> {
            assertThat(result.id()).isEqualTo(payment.id());
            assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
        });
        assertThat(completions.getFirst()).isEqualTo(completions.getLast());
        assertThat(transactionCount(payment.id())).isEqualTo(1);
        assertThrows(DuplicateKeyException.class, () -> jdbc.update("""
                INSERT INTO payment_service.transaction_records
                    (payment_id, idempotency_key, operation, status, amount, currency)
                VALUES (?, 'mock-completion', 'CAPTURE', 'SUCCEEDED', 10.00, 'USD')
                """, payment.id()));
        assertThat(service.initiate(key, request).payment()).isEqualTo(completions.getFirst());
    }

    @Test
    void differentKeysCompeteForOneActiveAttemptAndDatabaseRejectsBypass() throws Exception {
        var request = checkout();
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();
        var outcomes = race(() -> statusOf(() -> service.initiate(first, request)),
                () -> statusOf(() -> service.initiate(second, request)));

        assertThat(outcomes).containsExactlyInAnyOrder(201, 409);
        var pending = service.forOrder(request.orderId()).getFirst();
        assertThrows(DuplicateKeyException.class, () -> jdbc.update("""
                INSERT INTO payment_service.payments (order_id, idempotency_key, provider, currency, amount)
                VALUES (?, ?, 'MOCK', 'USD', 10.00)
                """, request.orderId(), UUID.randomUUID().toString()));

        var completionOutcomes = race(
                () -> statusOf(() -> service.simulate(pending.id(), MockPaymentProvider.Outcome.SUCCESS)),
                () -> statusOf(() -> service.simulate(pending.id(), MockPaymentProvider.Outcome.FAILED)));
        assertThat(completionOutcomes).containsExactlyInAnyOrder(200, 409);
        assertThat(transactionCount(pending.id())).isEqualTo(1);
    }

    @Test
    void failureReleasesActiveSlotWithoutReusingTheOriginalRequestKey() {
        var request = checkout();
        String key = UUID.randomUUID().toString();
        var first = service.initiate(key, request).payment();
        var failed = service.simulate(first.id(), MockPaymentProvider.Outcome.FAILED);
        var retry = service.initiate(UUID.randomUUID().toString(), request);

        assertThat(retry.created()).isTrue();
        assertThat(retry.payment().id()).isNotEqualTo(first.id());
        assertThat(service.initiate(key, request).payment()).isEqualTo(failed);
        assertThat(service.forOrder(request.orderId())).hasSize(2);
    }

    private InitiatePaymentRequest checkout() {
        UUID orderId = UUID.randomUUID();
        var amount = new BigDecimal("10.00");
        when(orders.get(orderId)).thenReturn(new OrderClient.OrderSnapshot(orderId, "PENDING_PAYMENT", amount, "USD"));
        return new InitiatePaymentRequest(orderId, amount, "USD");
    }

    private long transactionCount(UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM payment_service.transaction_records WHERE payment_id = ?",
                Long.class, id);
    }

    private int statusOf(Supplier<?> operation) {
        try {
            return operation.get() instanceof PaymentService.Initiation result && result.created() ? 201 : 200;
        } catch (ResponseStatusException exception) {
            return exception.getStatusCode().value();
        }
    }

    private <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var left = workers.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return first.call();
            });
            var right = workers.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return second.call();
            });
            try {
                return List.of(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS));
            } finally {
                left.cancel(true);
                right.cancel(true);
            }
        }
    }
}
