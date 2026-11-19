package com.aniketh.retail.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.UUID;

@RestController
@RequestMapping("/api")
class OrderController {

    private static final String SORT_PATTERN = "(createdAt|updatedAt|totalAmount|orderNumber),(asc|desc)";
    private final OrderService service;

    OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping("/orders")
    ResponseEntity<OrderResponse> create(
            @RequestHeader("Idempotency-Key")
            @Pattern(regexp = "[!-~]{1,128}") String key,
            @Valid @RequestBody CreateOrderRequest request) {
        var result = service.create(key, request);
        if (!result.created()) {
            return ResponseEntity.ok(result.order());
        }
        var location = ServletUriComponentsBuilder.fromCurrentRequestUri()
                .path("/{id}").buildAndExpand(result.order().id()).toUri();
        return ResponseEntity.created(location).body(result.order());
    }

    @GetMapping("/orders/{id}")
    OrderResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/orders")
    PagedModel<OrderSummary> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") @Pattern(regexp = SORT_PATTERN) String sort) {
        return new PagedModel<>(service.list(pageRequest(page, size, sort)));
    }

    @GetMapping("/customers/{customerId}/orders")
    PagedModel<OrderSummary> customerHistory(
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") @Pattern(regexp = SORT_PATTERN) String sort) {
        return new PagedModel<>(service.customerHistory(customerId, pageRequest(page, size, sort)));
    }

    @PatchMapping("/orders/{id}/status")
    OrderResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return service.updateStatus(id, request.status());
    }

    private PageRequest pageRequest(int page, int size, String sort) {
        if ((long) page * size > Integer.MAX_VALUE) {
            throw OrderException.badRequest("The requested page offset is too large.");
        }
        String[] parts = sort.split(",");
        Sort.Direction direction = Sort.Direction.fromString(parts[1]);
        return PageRequest.of(page, size, Sort.by(direction, parts[0], "id"));
    }
}
