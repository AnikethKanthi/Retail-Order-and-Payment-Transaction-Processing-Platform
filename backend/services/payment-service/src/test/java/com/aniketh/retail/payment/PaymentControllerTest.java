package com.aniketh.retail.payment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {
    private static final UUID ORDER = UUID.randomUUID();
    private static final UUID ID = UUID.randomUUID();
    private static final String REQUEST = """
            {"orderId":"%s","amount":10.00,"currency":"USD"}
            """.formatted(ORDER);

    @Autowired MockMvc mvc;
    @MockitoBean PaymentService service;

    @Test
    void initiatesAndListsPaymentByOrder() throws Exception {
        var payment = payment(PaymentStatus.PENDING);
        when(service.initiate(eq("checkout"), any())).thenReturn(new PaymentService.Initiation(payment, true));
        when(service.forOrder(ORDER)).thenReturn(List.of(payment));

        mvc.perform(post("/api/payments").header("Idempotency-Key", "checkout")
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/payments/" + ID))
                .andExpect(jsonPath("$.status").value("PENDING"));
        mvc.perform(get("/api/orders/{id}/payments", ORDER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderId").value(ORDER.toString()));
    }

    @Test
    void tracksSimulatedOutcomeAndMissingPayment() throws Exception {
        var payment = payment(PaymentStatus.SUCCESS);
        when(service.simulate(ID, MockPaymentProvider.Outcome.SUCCESS)).thenReturn(payment);
        when(service.get(ID)).thenReturn(payment);
        var missing = UUID.randomUUID();
        when(service.get(missing)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment was not found."));

        mvc.perform(post("/api/payments/{id}/simulate", ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":\"SUCCESS\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        mvc.perform(get("/api/payments/{id}", ID)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));
        mvc.perform(get("/api/payments/{id}", missing)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidAmountUnknownFieldsAndNumericOutcome() throws Exception {
        for (String payload : List.of(REQUEST.replace("10.00", "0"), REQUEST.replace("}", ",\"unexpected\":true}"))) {
            mvc.perform(post("/api/payments").header("Idempotency-Key", "checkout")
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/payments/{id}/simulate", ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"outcome\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    private Payment payment(PaymentStatus status) {
        return new Payment(ID, ORDER, "MOCK", status, BigDecimal.TEN, "USD", Instant.EPOCH, Instant.EPOCH);
    }
}
