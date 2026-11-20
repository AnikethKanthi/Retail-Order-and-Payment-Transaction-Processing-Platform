# Order Service

Maven module: `com.aniketh.retail:order-service`.

Owns `order_service.customers`, `orders`, and `order_items`. It is the authority
for customer profiles and checkout snapshots. Use only the order runtime database
role; see [database ownership](../../../docs/database-design.md).

## Run locally

Apply the [database migrations and runtime grants](../../../database/README.md)
first. Set `ORDER_DB_USER` to the login granted membership in `retail_order_app`
and set `ORDER_DB_PASSWORD` to its password. `ORDER_DB_URL` defaults to
`jdbc:postgresql://localhost:5432/retail_platform`. The service validates the
schema on startup. Use an existing active customer ID when creating orders.

From `backend/` in PowerShell:

```powershell
.\mvnw.cmd -pl services/order-service -am clean verify
java -jar services/order-service/target/order-service-0.1.0-SNAPSHOT.jar
```

The HTTP port defaults to `8081`. Set `SERVER_PORT` to override it.

## Order API

`POST /api/orders` requires an `Idempotency-Key` header containing 1–128 printable
ASCII characters without spaces. Example JSON body:

```json
{
  "customerId": "4f85e0da-40a6-4a72-aa50-71a499d3c990",
  "currency": "USD",
  "shippingAmount": 2.00,
  "items": [
    {"sku": "SKU-1", "productName": "Notebook", "quantity": 2, "unitPrice": 5.00}
  ]
}
```

Optional `shippingAmount`, line `discountAmount`, and line `taxAmount` default
to zero. Supply at least one item with a positive JSON integer quantity and
visible SKU and product-name text. The service validates the submitted product
snapshot fields. Product existence, stock, and catalog prices require an
inventory or catalog integration. Monetary inputs must fit the currency's minor
units and the storage range. The service computes the subtotal as the sum of
quantity times unit price, subtracts line discounts, adds line taxes and
shipping. The response includes the `PENDING_PAYMENT` order, its line items,
and the PostgreSQL-generated totals. A new order
returns `201 Created` with a `Location` header. A retry with equivalent checkout
data returns the existing order as `200 OK`. Reusing the key with different data
returns `409 Conflict`.

`GET /api/orders/{id}` returns the order and its items in line order, or `404`
for an unknown UUID. Request errors use `application/problem+json`. Malformed or
invalid inputs return `400`, and business validation failures such as an inactive
customer or a discount above the line gross amount return `422`.
Unexpected persistence failures return a generic `500` response. The service
logs the exception category and SQL state without logging request data.

`GET /api/orders` lists order summaries. `GET /api/customers/{customerId}/orders`
lists summaries for one customer, with an empty page when there are no orders.
Both return `content` and `page` metadata. They accept zero-based `page`
(default 0), `size` (default 20, range 1–100), and a single `sort` value in
`field,direction` format. Sort fields are `createdAt`, `updatedAt`, `totalAmount`,
and `orderNumber`. The default is `createdAt,desc`, and the order ID breaks ties.
Use the order detail endpoint for line items.

`PATCH /api/orders/{id}/status` accepts a body such as `{"status":"CONFIRMED"}`.
An order can move from `PENDING_PAYMENT` to `CONFIRMED`, `CANCELLED`, or `FAILED`.
These three states are terminal. Repeating the current lifecycle status returns
the order without another update. Invalid transitions return `409`, and unknown
orders return `404`. Historical `FULFILLED` orders remain readable.

## PostgreSQL rollback test

The regular test run needs no database. To run the rollback test, apply the
migrations to a disposable PostgreSQL database, set `ORDER_DB_URL`,
`ORDER_DB_USER`, and `ORDER_DB_PASSWORD` for its schema owner, and set
`ORDER_DB_ROLLBACK_TEST=true` for the Maven run. The test installs a temporary
check constraint to reject the second line insert, checks that no order or lines
remain, then retries and verifies the persisted totals. The test removes its
fixtures and constraint at completion.
