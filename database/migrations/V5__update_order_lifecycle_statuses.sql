ALTER TABLE order_service.orders DROP CONSTRAINT orders_status_ck;

UPDATE order_service.orders SET status = 'PENDING_PAYMENT' WHERE status = 'PENDING';
UPDATE order_service.orders SET status = 'FAILED' WHERE status = 'PAYMENT_FAILED';

ALTER TABLE order_service.orders
    ALTER COLUMN status SET DEFAULT 'PENDING_PAYMENT',
    ADD CONSTRAINT orders_status_ck CHECK (
        status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'FAILED', 'FULFILLED')
    );
