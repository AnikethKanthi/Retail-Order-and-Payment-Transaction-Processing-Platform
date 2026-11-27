package com.aniketh.retail.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

record ReserveRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[^\\p{Cc}]+") String sku,
        @NotBlank @Size(max = 40) @Pattern(regexp = "[^\\p{Cc}]+") String warehouseCode,
        @NotNull @Positive Long quantity) {
}
