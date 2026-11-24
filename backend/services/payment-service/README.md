# Payment Service

Maven module: `com.aniketh.retail:payment-service`.

Owns `payment_service.payments` and append-only `transaction_records`. Order IDs
are external references validated through the order service. Use only the payment
runtime database role; see [database ownership](../../../docs/database-design.md).

## Run locally

Apply the [database migrations and runtime grants](../../../database/README.md).
Set `PAYMENT_DB_USER` to the login granted membership in `retail_payment_app`
and set `PAYMENT_DB_PASSWORD`. `PAYMENT_DB_URL` defaults to
`jdbc:postgresql://localhost:5432/retail_platform`. Set `ORDER_SERVICE_URL` if
the Order Service is listening somewhere other than `http://localhost:8081`.
The Payment Service defaults to port `8082`, configurable with `SERVER_PORT`.

From `backend/` in PowerShell:

```powershell
.\mvnw.cmd -pl services/payment-service -am clean verify
java -jar services/payment-service/target/payment-service-0.1.0-SNAPSHOT.jar
```

## Mock payment API

- `POST /api/payments` takes `orderId`, `amount`, and `currency` as JSON and
  requires an `Idempotency-Key` header. The order must be awaiting payment, and
  the amount and currency must match its total. Creation returns `201` with
  status `PENDING`. An equivalent retry returns the existing payment as `200`.
  A different request with the same key or a second active attempt returns `409`.
- `POST /api/payments/{id}/simulate` takes `{"outcome":"SUCCESS"}` or
  `{"outcome":"FAILED"}`. The mock provider records an append-only transaction
  and updates the payment atomically. Repeating the outcome returns the same
  result. Changing the outcome returns `409`. A failed payment allows a new
  initiation with a new key while the order is awaiting payment.
- `GET /api/payments/{id}` retrieves a payment. Unknown IDs return `404`.
- `GET /api/orders/{orderId}/payments` lists payment attempts for that order.

The provider is a local simulator. The API accepts no payment credentials and
makes no calls to payment networks. The REST status `SUCCESS` maps to the
existing database status `CAPTURED`. Simulation updates only Payment Service
data, so Order Service status changes are a separate operation.
