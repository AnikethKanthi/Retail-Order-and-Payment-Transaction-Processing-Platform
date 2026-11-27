package com.aniketh.retail.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
class InventoryController {
    private final InventoryService service;

    InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping("/stock")
    Stock stock(@RequestParam @NotBlank @Size(max = 64) String sku,
                @RequestParam @NotBlank @Size(max = 40) String warehouseCode) {
        return service.stock(sku, warehouseCode);
    }

    @GetMapping("/availability")
    Availability availability(@RequestParam @NotBlank @Size(max = 64) String sku,
                              @RequestParam @NotBlank @Size(max = 40) String warehouseCode,
                              @RequestParam @Positive long quantity) {
        Stock stock = service.stock(sku, warehouseCode);
        return new Availability(stock.availableQuantity() >= quantity, stock.availableQuantity());
    }

    @PutMapping("/reservations/{id}")
    Reservation reserve(@PathVariable UUID id, @Valid @RequestBody ReserveRequest request) {
        return service.reserve(id, request);
    }

    @GetMapping("/reservations/{id}")
    Reservation reservation(@PathVariable UUID id) {
        return service.reservation(id);
    }

    @PostMapping("/reservations/{id}/release")
    Reservation release(@PathVariable UUID id) {
        return service.finish(id, Reservation.Status.RELEASED);
    }

    @PostMapping("/reservations/{id}/deduct")
    Reservation deduct(@PathVariable UUID id) {
        return service.finish(id, Reservation.Status.DEDUCTED);
    }

    record Availability(boolean available, long availableQuantity) {
    }
}
