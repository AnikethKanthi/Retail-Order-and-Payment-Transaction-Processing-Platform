package com.aniketh.retail.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
@Validated
class InventoryService {
    private final InventoryRepository repository;

    InventoryService(InventoryRepository repository) {
        this.repository = repository;
    }

    public Stock stock(String sku, String warehouse) {
        return repository.findStock(sku.strip(), warehouse.strip()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock was not found."));
    }

    public Reservation reservation(UUID id) {
        return repository.findReservation(id).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation was not found."));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation reserve(UUID id, @NotNull @Valid ReserveRequest request) {
        repository.lockReservation(id);
        String sku = request.sku().strip();
        String warehouse = request.warehouseCode().strip();
        var existing = repository.findReservation(id);
        if (existing.isPresent()) {
            Reservation current = existing.get();
            if (!current.sku().equals(sku) || !current.warehouseCode().equals(warehouse)
                    || current.quantity() != request.quantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation ID belongs to a different request.");
            }
            return current;
        }

        Stock stock = stock(sku, warehouse);
        if (!repository.reserve(stock.id(), request.quantity())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient available stock.");
        }
        var reservation = new Reservation(id, stock.id(), sku, warehouse, request.quantity(), Reservation.Status.RESERVED);
        repository.insert(reservation);
        return reservation;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Reservation finish(UUID id, Reservation.Status status) {
        repository.lockReservation(id);
        Reservation current = reservation(id);
        if (current.status() == status) {
            return current;
        }
        if (current.status() != Reservation.Status.RESERVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Reservation already has a different final status.");
        }
        if (!repository.finish(current.inventoryId(), current.quantity(), status)) {
            throw new IllegalStateException("Inventory balance is inconsistent with its reservation.");
        }
        repository.finishReservation(id, status);
        return new Reservation(id, current.inventoryId(), current.sku(), current.warehouseCode(), current.quantity(), status);
    }
}
