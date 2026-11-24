package com.aniketh.retail.payment;

public enum PaymentStatus {
    PENDING, SUCCESS, FAILED, AUTHORIZED, CANCELLED, PARTIALLY_REFUNDED, REFUNDED;

    static PaymentStatus fromDatabase(String status) {
        return status.equals("CAPTURED") ? SUCCESS : valueOf(status);
    }
}
