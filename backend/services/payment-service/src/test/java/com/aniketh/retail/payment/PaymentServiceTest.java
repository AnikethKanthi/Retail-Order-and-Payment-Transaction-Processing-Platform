package com.aniketh.retail.payment;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final BigDecimal AMOUNT = new BigDecimal("10.00");
    private static final InitiatePaymentRequest REQUEST = new InitiatePaymentRequest(ORDER_ID, AMOUNT, "USD");

    @Mock PaymentRepository payments;
    @Mock OrderClient orders;
    @Mock MockPaymentProvider provider;
    @InjectMocks PaymentService service;

    @Test
    void validatesOrderAndStartsPendingPayment() {
        var pending = payment(PaymentStatus.PENDING);
        when(orders.get(ORDER_ID)).thenReturn(order("PENDING_PAYMENT", AMOUNT));
        when(payments.insert("key", REQUEST)).thenReturn(Optional.of(pending));

        assertThat(service.initiate("key", REQUEST)).isEqualTo(new PaymentService.Initiation(pending, true));
        verify(payments).lockInitiation("key", ORDER_ID);
        verifyNoInteractions(provider);
    }

    @Test
    void replaysMatchingRequestAndRejectsKeyReuse() {
        var success = payment(PaymentStatus.SUCCESS);
        when(payments.findByKey("key")).thenReturn(Optional.of(success));

        assertThat(service.initiate("key", REQUEST)).isEqualTo(new PaymentService.Initiation(success, false));
        var mismatched = new InitiatePaymentRequest(ORDER_ID, new BigDecimal("11"), "USD");
        assertThat(assertThrows(ResponseStatusException.class, () -> service.initiate("key", mismatched))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(payments, never()).insert(anyString(), any());
        verifyNoInteractions(orders);
    }

    @Test
    void rejectsWrongAmountAndIneligibleOrder() {
        when(orders.get(ORDER_ID)).thenReturn(order("PENDING_PAYMENT", BigDecimal.ONE),
                order("CANCELLED", AMOUNT));

        assertThat(assertThrows(ResponseStatusException.class, () -> service.initiate("amount", REQUEST))
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(assertThrows(ResponseStatusException.class, () -> service.initiate("status", REQUEST))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(payments, never()).insert(anyString(), any());
    }

    @Test
    void rejectsSecondActiveAttempt() {
        when(payments.hasActiveAttempt(ORDER_ID)).thenReturn(true);
        assertThat(assertThrows(ResponseStatusException.class, () -> service.initiate("key", REQUEST))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verifyNoInteractions(orders, provider);
    }

    @Test
    void resolvesInsertConflictUsingCommittedRequest() {
        var existing = payment(PaymentStatus.PENDING);
        when(orders.get(ORDER_ID)).thenReturn(order("PENDING_PAYMENT", AMOUNT));
        when(payments.findByKey("key")).thenReturn(Optional.empty(), Optional.of(existing));
        when(payments.insert("key", REQUEST)).thenReturn(Optional.empty());

        assertThat(service.initiate("key", REQUEST)).isEqualTo(new PaymentService.Initiation(existing, false));
    }

    @Test
    void treatsInsertConflictWithoutMatchingKeyAsAnActiveAttempt() {
        when(orders.get(ORDER_ID)).thenReturn(order("PENDING_PAYMENT", AMOUNT));
        when(payments.insert("key", REQUEST)).thenReturn(Optional.empty());

        assertThat(assertThrows(ResponseStatusException.class, () -> service.initiate("key", REQUEST))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void recordsBothMockOutcomes() {
        for (var outcome : MockPaymentProvider.Outcome.values()) {
            var pending = payment(PaymentStatus.PENDING);
            var transaction = new MockPaymentProvider().complete(pending, outcome);
            var completed = payment(transaction.outcome());
            when(payments.lockPayment(PAYMENT_ID)).thenReturn(Optional.of(pending));
            when(provider.complete(pending, outcome)).thenReturn(transaction);
            when(payments.complete(transaction)).thenReturn(completed);

            assertThat(service.simulate(PAYMENT_ID, outcome)).isEqualTo(completed);
            verify(payments).append(transaction);
        }
    }

    @Test
    void simulationRetryDoesNotAppendAndConflictingOutcomeIsRejected() {
        var success = payment(PaymentStatus.SUCCESS);
        when(payments.lockPayment(PAYMENT_ID)).thenReturn(Optional.of(success));

        assertThat(service.simulate(PAYMENT_ID, MockPaymentProvider.Outcome.SUCCESS)).isEqualTo(success);
        assertThat(assertThrows(ResponseStatusException.class,
                () -> service.simulate(PAYMENT_ID, MockPaymentProvider.Outcome.FAILED)).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(payments, never()).append(any());
        verifyNoInteractions(provider);
    }

    private OrderClient.OrderSnapshot order(String status, BigDecimal amount) {
        return new OrderClient.OrderSnapshot(ORDER_ID, status, amount, "USD");
    }

    private Payment payment(PaymentStatus status) {
        return new Payment(PAYMENT_ID, ORDER_ID, "MOCK", status, AMOUNT, "USD", Instant.EPOCH, Instant.EPOCH);
    }
}
