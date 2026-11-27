package com.aniketh.retail.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    private final UUID id = UUID.randomUUID();
    private final UUID stockId = UUID.randomUUID();
    private final ReserveRequest request = new ReserveRequest("SKU", "WH", 3L);

    @Mock InventoryRepository repository;
    @InjectMocks InventoryService service;

    @Test
    void createsReservationAndRejectsInsufficientStock() {
        when(repository.findStock("SKU", "WH")).thenReturn(Optional.of(new Stock(stockId, "SKU", "WH", 3, 0, 3)));
        when(repository.reserve(stockId, 3)).thenReturn(true, false);

        var result = service.reserve(id, request);
        assertThat(result.status()).isEqualTo(Reservation.Status.RESERVED);
        verify(repository).insert(result);

        assertThat(assertThrows(ResponseStatusException.class, () -> service.reserve(UUID.randomUUID(), request))
                .getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(repository, times(1)).insert(any());
    }

    @Test
    void returnsExistingReservationAndRejectsMismatchedRetry() {
        var existing = new Reservation(id, stockId, "SKU", "WH", 3, Reservation.Status.RESERVED);
        when(repository.findReservation(id)).thenReturn(Optional.of(existing));

        assertThat(service.reserve(id, request)).isEqualTo(existing);
        assertThat(assertThrows(ResponseStatusException.class,
                () -> service.reserve(id, new ReserveRequest("SKU", "WH", 4L))).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(repository, never()).reserve(any(), anyLong());
    }

    @Test
    void retriesSameTerminalOperationAndRejectsConflictingOperation() {
        var released = new Reservation(id, stockId, "SKU", "WH", 3, Reservation.Status.RELEASED);
        when(repository.findReservation(id)).thenReturn(Optional.of(released));

        assertThat(service.finish(id, Reservation.Status.RELEASED)).isEqualTo(released);
        assertThat(assertThrows(ResponseStatusException.class,
                () -> service.finish(id, Reservation.Status.DEDUCTED)).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        verify(repository, never()).finish(any(), anyLong(), any());
    }

    @Test
    void missingStockAndReservationsReturnNotFound() {
        assertThat(assertThrows(ResponseStatusException.class, () -> service.stock("SKU", "WH")).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(assertThrows(ResponseStatusException.class, () -> service.reservation(id)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
