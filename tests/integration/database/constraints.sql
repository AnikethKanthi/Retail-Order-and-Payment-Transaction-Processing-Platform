-- Run against a disposable migrated database as the migration owner.
BEGIN;

CREATE FUNCTION pg_temp.expect_failure(statement TEXT, expected_state TEXT)
RETURNS VOID LANGUAGE plpgsql AS $$
BEGIN
    BEGIN
        EXECUTE statement;
    EXCEPTION WHEN OTHERS THEN
        IF SQLSTATE <> expected_state THEN
            RAISE EXCEPTION 'Expected SQLSTATE %, got % for %: %',
                expected_state, SQLSTATE, statement, SQLERRM;
        END IF;
        RETURN;
    END;
    RAISE EXCEPTION 'Expected SQLSTATE %, but statement succeeded: %', expected_state, statement;
END;
$$;

INSERT INTO order_service.customers (id, email, display_name)
VALUES ('10000000-0000-0000-0000-000000000001', 'db-check@example.invalid', 'Database Check');

INSERT INTO order_service.orders
    (id, order_number, customer_id, idempotency_key, currency, subtotal,
     discount_amount, tax_amount, shipping_amount)
VALUES ('20000000-0000-0000-0000-000000000001', 'DB-CHECK-1',
    '10000000-0000-0000-0000-000000000001', 'db-check-order', 'USD', 24.6912, 1, 2, 3);

INSERT INTO order_service.order_items
    (order_id, line_number, sku, product_name, quantity, unit_price, discount_amount, tax_amount)
VALUES ('20000000-0000-0000-0000-000000000001', 1, 'DB-CHECK-SKU', 'Test Item', 2, 12.3456, 1, 2);

INSERT INTO payment_service.payments
    (id, order_id, idempotency_key, provider, provider_payment_id, currency, amount)
VALUES ('30000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001', 'db-check-payment', 'test-provider', 'test-pay-1', 'USD', 28.6912);

INSERT INTO payment_service.transaction_records
    (payment_id, idempotency_key, operation, status, currency, amount)
VALUES ('30000000-0000-0000-0000-000000000001', 'db-check-capture', 'CAPTURE', 'SUCCEEDED', 'USD', 28.6912);

INSERT INTO inventory_service.inventory (sku, warehouse_code, on_hand_quantity, reserved_quantity)
VALUES ('DB-CHECK-SKU', 'TEST-WAREHOUSE', 10, 2);

DO $$
BEGIN
    IF (SELECT total_amount FROM order_service.orders WHERE order_number = 'DB-CHECK-1') <> 28.6912
        OR (SELECT line_total FROM order_service.order_items
            WHERE order_id = '20000000-0000-0000-0000-000000000001') <> 25.6912
        OR (SELECT available_quantity FROM inventory_service.inventory
            WHERE sku = 'DB-CHECK-SKU' AND warehouse_code = 'TEST-WAREHOUSE') <> 8 THEN
        RAISE EXCEPTION 'Generated values are incorrect';
    END IF;
    IF EXISTS (
        SELECT 1 FROM pg_constraint c
        JOIN pg_class child ON child.oid = c.conrelid
        JOIN pg_class parent ON parent.oid = c.confrelid
        JOIN pg_namespace n ON n.oid = child.relnamespace
        WHERE c.contype = 'f' AND n.nspname IN ('order_service', 'payment_service', 'inventory_service')
            AND child.relnamespace <> parent.relnamespace
    ) THEN
        RAISE EXCEPTION 'Cross-service foreign key found';
    END IF;
END;
$$;

SELECT pg_temp.expect_failure($s$INSERT INTO order_service.customers (email, display_name)
    VALUES ('DB-CHECK@EXAMPLE.INVALID', 'Duplicate')$s$, '23505');
SELECT pg_temp.expect_failure($s$UPDATE order_service.orders SET customer_id =
    'ffffffff-ffff-ffff-ffff-ffffffffffff' WHERE order_number = 'DB-CHECK-1'$s$, '23503');
SELECT pg_temp.expect_failure($s$UPDATE order_service.orders SET subtotal = -1
    WHERE order_number = 'DB-CHECK-1'$s$, '23514');
SELECT pg_temp.expect_failure($s$UPDATE order_service.orders SET subtotal = 'NaN'
    WHERE order_number = 'DB-CHECK-1'$s$, '23514');
SELECT pg_temp.expect_failure($s$UPDATE order_service.orders SET discount_amount = 100
    WHERE order_number = 'DB-CHECK-1'$s$, '23514');
SELECT pg_temp.expect_failure($s$UPDATE order_service.orders SET currency = 'usd'
    WHERE order_number = 'DB-CHECK-1'$s$, '23514');
SELECT pg_temp.expect_failure($s$UPDATE order_service.orders SET status = 'INVALID'
    WHERE order_number = 'DB-CHECK-1'$s$, '23514');
SELECT pg_temp.expect_failure($s$UPDATE order_service.orders SET idempotency_key = ' '
    WHERE order_number = 'DB-CHECK-1'$s$, '23514');
SELECT pg_temp.expect_failure($s$UPDATE order_service.orders SET subtotal = 1000000000000000
    WHERE order_number = 'DB-CHECK-1'$s$, '22003');
SELECT pg_temp.expect_failure($s$UPDATE order_service.orders SET updated_at = created_at - INTERVAL '1 second'
    WHERE order_number = 'DB-CHECK-1'$s$, '23514');
SELECT pg_temp.expect_failure($s$UPDATE order_service.order_items SET quantity = 0
    WHERE order_id = '20000000-0000-0000-0000-000000000001'$s$, '23514');
SELECT pg_temp.expect_failure($s$UPDATE order_service.order_items SET discount_amount = 100
    WHERE order_id = '20000000-0000-0000-0000-000000000001'$s$, '23514');
SELECT pg_temp.expect_failure($s$INSERT INTO order_service.order_items
    (order_id, line_number, sku, product_name, quantity, unit_price)
    VALUES ('20000000-0000-0000-0000-000000000001', 1, 'OTHER-SKU', 'Duplicate Line', 1, 10)$s$, '23505');
SELECT pg_temp.expect_failure($s$INSERT INTO payment_service.payments
    (order_id, idempotency_key, provider, currency, amount)
    VALUES ('20000000-0000-0000-0000-000000000001', 'db-check-payment', 'test-provider', 'USD', 1)$s$, '23505');
SELECT pg_temp.expect_failure($s$INSERT INTO payment_service.payments
    (order_id, idempotency_key, provider, provider_payment_id, currency, amount)
    VALUES ('20000000-0000-0000-0000-000000000001', 'other-payment', 'test-provider', 'test-pay-1', 'USD', 1)$s$, '23505');
SELECT pg_temp.expect_failure($s$UPDATE payment_service.payments SET amount = 0
    WHERE id = '30000000-0000-0000-0000-000000000001'$s$, '23514');
SELECT pg_temp.expect_failure($s$UPDATE payment_service.payments SET amount = 'NaN'
    WHERE id = '30000000-0000-0000-0000-000000000001'$s$, '23514');
SELECT pg_temp.expect_failure($s$INSERT INTO payment_service.transaction_records
    (payment_id, idempotency_key, operation, status, currency, amount)
    VALUES ('30000000-0000-0000-0000-000000000001', 'wrong-currency', 'CAPTURE', 'SUCCEEDED', 'EUR', 1)$s$, '23503');
SELECT pg_temp.expect_failure($s$INSERT INTO payment_service.transaction_records
    (payment_id, idempotency_key, operation, status, currency, amount)
    VALUES ('30000000-0000-0000-0000-000000000001', 'db-check-capture', 'CAPTURE', 'SUCCEEDED', 'USD', 1)$s$, '23505');
SELECT pg_temp.expect_failure($s$UPDATE payment_service.transaction_records SET amount = 1
    WHERE payment_id = '30000000-0000-0000-0000-000000000001'$s$, '55000');
SELECT pg_temp.expect_failure($s$DELETE FROM payment_service.transaction_records
    WHERE payment_id = '30000000-0000-0000-0000-000000000001'$s$, '55000');
SELECT pg_temp.expect_failure($s$DELETE FROM payment_service.payments
    WHERE id = '30000000-0000-0000-0000-000000000001'$s$, '23503');
SELECT pg_temp.expect_failure($s$DELETE FROM order_service.customers
    WHERE id = '10000000-0000-0000-0000-000000000001'$s$, '23503');
SELECT pg_temp.expect_failure($s$UPDATE inventory_service.inventory SET on_hand_quantity = -1
    WHERE sku = 'DB-CHECK-SKU'$s$, '23514');
SELECT pg_temp.expect_failure($s$UPDATE inventory_service.inventory SET reserved_quantity = 11
    WHERE sku = 'DB-CHECK-SKU'$s$, '23514');
SELECT pg_temp.expect_failure($s$INSERT INTO inventory_service.inventory (sku, warehouse_code)
    VALUES ('DB-CHECK-SKU', 'TEST-WAREHOUSE')$s$, '23505');

ROLLBACK;
