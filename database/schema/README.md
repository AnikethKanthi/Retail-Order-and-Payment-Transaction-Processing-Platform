# Schema Reference

The executable schema lives only in versioned migrations:

| Migration | Responsibility |
| --- | --- |
| [V1](../migrations/V1__create_service_schemas.sql) | Service schema boundaries |
| [V2](../migrations/V2__create_order_tables.sql) | Customers, orders, order items |
| [V3](../migrations/V3__create_payment_tables.sql) | Payments and transaction records |
| [V4](../migrations/V4__create_inventory_table.sql) | Inventory balances by SKU and warehouse |

[Runtime grants](runtime-grants.sql) is an optional operator provisioning script,
applied after migrations and role creation. It is not a schema snapshot or an
automatically executed migration. [Database design](../../docs/database-design.md)
explains table ownership and the constraints that require application logic.
