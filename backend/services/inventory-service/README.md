# Inventory Service

Maven module: `com.aniketh.retail:inventory-service`.

Owns `inventory_service.inventory` and `inventory_service.reservations`, including
stock and reserved balances by SKU and warehouse. Use the inventory runtime database role. See
[database ownership](../../../docs/database-design.md).

## Run locally

Apply migrations through V7 and reapply [runtime grants](../../../database/README.md).
Set `INVENTORY_DB_USER` to the login granted membership in `retail_inventory_app`
and set `INVENTORY_DB_PASSWORD`. `INVENTORY_DB_URL` defaults to
`jdbc:postgresql://localhost:5432/retail_platform`. The service listens on port
`8083` by default. Override it with `SERVER_PORT`.

From `backend/` in PowerShell:

```powershell
.\mvnw.cmd -pl services/inventory-service -am clean verify
java -jar services/inventory-service/target/inventory-service-0.1.0-SNAPSHOT.jar
```

## Inventory API

All endpoints are under `/api/inventory`. Stock rows are provisioned separately.

- `GET /stock?sku=SKU-1&warehouseCode=WH` returns on-hand, reserved, and available
  quantities. `GET /availability?sku=SKU-1&warehouseCode=WH&quantity=2` returns
  whether the requested quantity is available at the time of the lookup.
- `PUT /reservations/{id}` reserves stock for a client-generated UUID. Send
  `{"sku":"SKU-1","warehouseCode":"WH","quantity":2}` with a positive integer
  quantity. Repeating the same request returns the existing reservation.
- `GET /reservations/{id}` returns the reservation and its current status.
- `POST /reservations/{id}/release` returns reserved units to availability.
  `POST /reservations/{id}/deduct` consumes those units. Each operation is
  safe to repeat. The opposite operation after completion returns `409`.

Insufficient stock or reusing a reservation ID with different details returns
`409`. Unknown stock and reservation IDs return `404`. Reservation records
created by this service track the units affected by release and deduction.
Pre-existing aggregate reserved balances have no reservation IDs and remain
counted against availability.

Set `INVENTORY_DB_TEST=true` to run the PostgreSQL integration tests with the
connection settings above. Use a disposable migrated database because the
tests add stock and reservation records.
