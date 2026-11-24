package com.aniketh.retail.payment;

import java.math.BigDecimal;
import java.util.UUID;

record PaymentTransaction(UUID paymentId, String providerReference, PaymentStatus outcome,
                          BigDecimal amount, String currency) {
}
