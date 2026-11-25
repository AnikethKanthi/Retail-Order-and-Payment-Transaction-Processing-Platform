-- Use the same status set as the Payment Service. Failed, cancelled, and fully
-- refunded attempts do not occupy this slot.
CREATE UNIQUE INDEX payments_one_active_attempt_per_order_uq
    ON payment_service.payments (order_id)
    WHERE status IN ('PENDING', 'AUTHORIZED', 'CAPTURED', 'PARTIALLY_REFUNDED');
