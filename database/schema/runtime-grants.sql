-- Optional operator provisioning, AFTER migrations. Not a Flyway migration.
-- DBA must first create these non-owner roles (and separate login credentials).
-- Execute as the migration owner/DBA; existing deployments must be audited for
-- inherited memberships and PUBLIC grants as well. Never use an app role for DDL.
GRANT USAGE ON SCHEMA order_service TO retail_order_app;
GRANT SELECT, INSERT, UPDATE ON order_service.customers, order_service.orders,
    order_service.order_items TO retail_order_app;

GRANT USAGE ON SCHEMA payment_service TO retail_payment_app;
GRANT SELECT, INSERT, UPDATE ON payment_service.payments TO retail_payment_app;
GRANT SELECT, INSERT ON payment_service.transaction_records TO retail_payment_app;

GRANT USAGE ON SCHEMA inventory_service TO retail_inventory_app;
GRANT SELECT, INSERT, UPDATE ON inventory_service.inventory TO retail_inventory_app;

-- No grants for notification-service: it has no persistence in this phase.
-- New tables require explicit grants in a reviewed deployment change.
