package com.aniketh.retail.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Payment(UUID id, UUID orderId, String provider, PaymentStatus status,
                      BigDecimal amount, String currency, Instant createdAt, Instant updatedAt) {
}
