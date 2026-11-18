package com.aniketh.retail.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull UUID customerId,
        @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
        @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal shippingAmount,
        @NotEmpty List<@NotNull @Valid Item> items
) {
    public record Item(
            @NotBlank @Size(max = 64) @Pattern(regexp = "[^\\p{Cntrl}]+") String sku,
            @NotBlank @Size(max = 255) @Pattern(regexp = "[^\\p{Cntrl}]+") String productName,
            @NotNull @Positive Integer quantity,
            @NotNull @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal unitPrice,
            @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal discountAmount,
            @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal taxAmount
    ) {
    }
}
