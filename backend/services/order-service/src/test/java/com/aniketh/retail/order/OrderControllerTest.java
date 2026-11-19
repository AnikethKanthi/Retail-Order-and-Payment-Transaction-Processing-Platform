package com.aniketh.retail.order;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
        var order = new OrderResponse(ORDER_ID, "ORD-1", CUSTOMER, OrderStatus.PENDING_PAYMENT, "USD",
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN,
                null, null, List.of());
        when(service.create(eq("checkout"), any())).thenReturn(new OrderService.Creation(order, true));
        when(service.get(ORDER_ID)).thenReturn(order);

        mvc.perform(post("/api/orders").header("Idempotency-Key", "checkout")
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/orders/" + ORDER_ID))
                .andExpect(jsonPath("$.status").value("PENDING_PAYMENT"));

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

    @Test
    void listsOrdersWithDefaultPaginationAndStableSort() throws Exception {
        var pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        when(service.list(pageable)).thenReturn(new PageImpl<>(List.of(summary()), pageable, 1));

        mvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ORDER_ID.toString()))
                .andExpect(jsonPath("$.content[0].items").doesNotExist())
                .andExpect(jsonPath("$.page.size").value(20))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void retrievesCustomerHistoryWithRequestedPaginationAndSort() throws Exception {
        var pageable = PageRequest.of(1, 1, Sort.by(Sort.Direction.ASC, "totalAmount", "id"));
        when(service.customerHistory(CUSTOMER, pageable))
                .thenReturn(new PageImpl<>(List.of(summary()), pageable, 2));

        mvc.perform(get("/api/customers/{customerId}/orders", CUSTOMER)
                        .param("page", "1").param("size", "1").param("sort", "totalAmount,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].customerId").value(CUSTOMER.toString()))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void rejectsBadPaginationAndSort() throws Exception {
        mvc.perform(get("/api/orders").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders").param("page", "2147483647"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders").param("size", "101"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/customers/{customerId}/orders", CUSTOMER).param("sort", "idempotencyKey,asc"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void updatesOrderStatusAndReportsConflicts() throws Exception {
        var order = new OrderResponse(ORDER_ID, "ORD-1", CUSTOMER, OrderStatus.CONFIRMED, "USD",
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN,
                null, null, List.of());
        when(service.updateStatus(ORDER_ID, OrderStatus.CONFIRMED)).thenReturn(order);
        when(service.updateStatus(ORDER_ID, OrderStatus.FAILED))
                .thenThrow(OrderException.invalidTransition(OrderStatus.CONFIRMED, OrderStatus.FAILED));
        when(service.updateStatus(ORDER_ID, OrderStatus.CANCELLED)).thenThrow(OrderException.notFound(ORDER_ID));

        mvc.perform(patch("/api/orders/{id}/status", ORDER_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(patch("/api/orders/{id}/status", ORDER_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FAILED\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(patch("/api/orders/{id}/status", ORDER_ID).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsMissingUnknownAndNumericStatus() throws Exception {
        for (String payload : List.of("{}", "{\"status\":\"UNKNOWN\"}", "{\"status\":1}")) {
            mvc.perform(patch("/api/orders/{id}/status", ORDER_ID).contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMissingEmptyAndNullItems() throws Exception {
        for (String items : List.of("null", "[]", "[null]")) {
            var payload = "{\"customerId\":\"%s\",\"currency\":\"USD\",\"items\":%s}"
                    .formatted(CUSTOMER, items);
            mvc.perform(post("/api/orders").header("Idempotency-Key", "checkout")
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    private OrderSummary summary() {
        return new OrderSummary(ORDER_ID, "ORD-1", CUSTOMER, OrderStatus.PENDING_PAYMENT,
                "USD", BigDecimal.TEN, null, null);
    }
}
