package com.aniketh.retail.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
class PaymentController {
    private final PaymentService service;

    PaymentController(PaymentService service) {
        this.service = service;
    }

    @PostMapping("/payments")
    ResponseEntity<Payment> initiate(
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[!-~]{1,128}") String key,
            @Valid @RequestBody InitiatePaymentRequest request) {
        var result = service.initiate(key, request);
        if (!result.created()) {
            return ResponseEntity.ok(result.payment());
        }
        var location = ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}")
                .buildAndExpand(result.payment().id()).toUri();
        return ResponseEntity.created(location).body(result.payment());
    }

    @GetMapping("/payments/{id}")
    Payment get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/orders/{orderId}/payments")
    List<Payment> forOrder(@PathVariable UUID orderId) {
        return service.forOrder(orderId);
    }

    @PostMapping("/payments/{id}/simulate")
    Payment simulate(@PathVariable UUID id, @Valid @RequestBody Simulation request) {
        return service.simulate(id, request.outcome());
    }

    record Simulation(@NotNull MockPaymentProvider.Outcome outcome) {
    }
}
