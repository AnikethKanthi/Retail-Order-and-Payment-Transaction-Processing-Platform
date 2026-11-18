package com.aniketh.retail.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    private static final UUID CUSTOMER = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final String REQUEST = """
            {
              "customerId": "%s",
              "currency": "USD",
              "items": [{"sku":"SKU-1","productName":"Example","quantity":2,"unitPrice":5.00}]
            }
            """.formatted(CUSTOMER);

    @Autowired MockMvc mvc;
    @MockitoBean OrderService service;

    @Test
    void createsOrderWithLocationAndRetrievesIt() throws Exception {
        var order = new OrderResponse(ORDER_ID, "ORD-1", CUSTOMER, OrderStatus.PENDING, "USD",
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN,
                null, null, List.of());
        when(service.create(eq("checkout"), any())).thenReturn(new OrderService.Creation(order, true));
        when(service.get(ORDER_ID)).thenReturn(order);

        mvc.perform(post("/api/orders").header("Idempotency-Key", "checkout")
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/orders/" + ORDER_ID))
                .andExpect(jsonPath("$.status").value("PENDING"));

        mvc.perform(get("/api/orders/{id}", ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ORDER_ID.toString()));
    }

    @Test
    void reportsInvalidNestedFieldsAndInvalidIdempotencyHeader() throws Exception {
        mvc.perform(post("/api/orders").header("Idempotency-Key", "checkout")
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST.replace("\"quantity\":2", "\"quantity\":0")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors[0].field").value("items[0].quantity"));

        mvc.perform(post("/api/orders").header("Idempotency-Key", "invalid key")
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("Idempotency-Key"));

        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingHeaderAndFractionalQuantity() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Missing required header Idempotency-Key."));

        mvc.perform(post("/api/orders").header("Idempotency-Key", "checkout")
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST.replace("\"quantity\":2", "\"quantity\":1.5")))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void reportsMalformedAndUnknownOrderIds() throws Exception {
        when(service.get(ORDER_ID)).thenThrow(OrderException.notFound(ORDER_ID));

        mvc.perform(get("/api/orders/not-a-uuid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders/{id}", ORDER_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }
}
