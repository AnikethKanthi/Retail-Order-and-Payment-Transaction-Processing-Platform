-- One database for the initial deployment; no cross-service SQL dependencies.
CREATE SCHEMA order_service;
CREATE SCHEMA payment_service;
CREATE SCHEMA inventory_service;

REVOKE ALL ON SCHEMA order_service, payment_service, inventory_service FROM PUBLIC;
