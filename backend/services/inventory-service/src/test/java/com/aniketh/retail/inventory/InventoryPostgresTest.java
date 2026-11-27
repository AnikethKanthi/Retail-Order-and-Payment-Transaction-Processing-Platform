package com.aniketh.retail.inventory;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "INVENTORY_DB_TEST", matches = "true")
class InventoryPostgresTest {
    @Autowired InventoryService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test
    void competingReservationsCannotOversell() throws Exception {
        String sku = stock(5);
        var request = new ReserveRequest(sku, "WH", 4L);

        var results = race(() -> reserveStatus(UUID.randomUUID(), request), () -> reserveStatus(UUID.randomUUID(), request));

        assertThat(results).containsExactlyInAnyOrder(200, 409);
        assertBalance(sku, 5, 4, 1);
        var count = jdbc.queryForObject("""
                SELECT count(*) FROM inventory_service.reservations WHERE inventory_id = ?
                """, Long.class, service.stock(sku, "WH").id());
        assertThat(count).isEqualTo(1L);
    }

    @Test
    void concurrentRetriesAndCompetingFinalActionsChangeStockOnce() throws Exception {
        String sku = stock(5);
        var id = UUID.randomUUID();
        var request = new ReserveRequest(sku, "WH", 4L);

        var results = race(() -> service.reserve(id, request), () -> service.reserve(id, request));
        assertThat(results.getFirst()).isEqualTo(results.getLast());
        assertBalance(sku, 5, 4, 1);

        var outcomes = race(() -> finishStatus(id, Reservation.Status.RELEASED),
                () -> finishStatus(id, Reservation.Status.DEDUCTED));
        assertThat(outcomes).containsExactlyInAnyOrder(200, 409);
        var current = service.reservation(id);
        assertThat(service.finish(id, current.status())).isEqualTo(current);
        long remaining = current.status() == Reservation.Status.DEDUCTED ? 1 : 5;
        assertBalance(sku, remaining, 0, remaining);
    }

    @Test
    void restEndpointsValidateQuantitiesAndApplyReleaseAndDeduction() throws Exception {
        String sku = stock(5);
        var id = UUID.randomUUID();
        var retry = UUID.randomUUID();
        var reservationUrl = "/api/inventory/reservations/{id}";
        var payload = "{\"sku\":\"%s\",\"warehouseCode\":\"WH\",\"quantity\":%s}";

        mvc.perform(get("/api/inventory/availability").param("sku", sku)
                        .param("warehouseCode", "WH").param("quantity", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));

        for (String quantity : List.of("0", "-1", "1.5", "\"2\"")) {
            mvc.perform(put(reservationUrl, id).contentType("application/json").content(payload.formatted(sku, quantity)))
                    .andExpect(status().isBadRequest());
        }
        assertThrows(ConstraintViolationException.class,
                () -> service.reserve(id, new ReserveRequest(sku, "WH", -1L)));

        mvc.perform(put(reservationUrl, id).contentType("application/json").content(payload.formatted(sku, "5")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESERVED"));
        assertBalance(sku, 5, 5, 0);
        mvc.perform(post(reservationUrl + "/release", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
        assertBalance(sku, 5, 0, 5);
        mvc.perform(put(reservationUrl, retry).contentType("application/json").content(payload.formatted(sku, "5")))
                .andExpect(status().isOk());
        mvc.perform(post(reservationUrl + "/deduct", retry)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEDUCTED"));
        assertBalance(sku, 0, 0, 0);
        mvc.perform(get(reservationUrl, retry)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEDUCTED"));

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                UPDATE inventory_service.inventory SET on_hand_quantity = -1 WHERE sku = ? AND warehouse_code = 'WH'
                """, sku));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                UPDATE inventory_service.inventory SET reserved_quantity = -1 WHERE sku = ? AND warehouse_code = 'WH'
                """, sku));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                UPDATE inventory_service.inventory SET reserved_quantity = 1 WHERE sku = ? AND warehouse_code = 'WH'
                """, sku));
        assertBalance(sku, 0, 0, 0);
    }

    private String stock(long quantity) {
        String sku = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO inventory_service.inventory (sku, warehouse_code, on_hand_quantity) VALUES (?, 'WH', ?)
                """, sku, quantity);
        return sku;
    }

    private void assertBalance(String sku, long onHand, long reserved, long available) {
        Stock stock = service.stock(sku, "WH");
        assertThat(stock.onHandQuantity()).isEqualTo(onHand);
        assertThat(stock.reservedQuantity()).isEqualTo(reserved);
        assertThat(stock.availableQuantity()).isEqualTo(available);
    }

    private int reserveStatus(UUID id, ReserveRequest request) {
        try {
            service.reserve(id, request);
            return HttpStatus.OK.value();
        } catch (ResponseStatusException exception) {
            return exception.getStatusCode().value();
        }
    }

    private int finishStatus(UUID id, Reservation.Status outcome) {
        try {
            service.finish(id, outcome);
            return HttpStatus.OK.value();
        } catch (ResponseStatusException exception) {
            return exception.getStatusCode().value();
        }
    }

    private <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var left = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return first.call();
            });
            var right = executor.submit(() -> {
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
