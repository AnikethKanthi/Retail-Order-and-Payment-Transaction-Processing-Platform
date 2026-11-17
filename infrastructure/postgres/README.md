# PostgreSQL Operations

Initial target: PostgreSQL 17, UTF-8 database `retail_platform`, connections using
UTC for predictable timestamp display. `TIMESTAMPTZ` stores instants independently
of session display timezone. Enforce TLS for remote deployments in the connection
configuration. No database server or credentials are installed by this repository.

## Provisioning responsibilities

The DBA creates a dedicated login `retail_migrator`, makes it the database owner,
and keeps its credentials limited to the migration deployment job. This login
physically owns the service tables and schemas it creates. Logical data ownership
belongs to the service listed in the database design. No runtime login may own
tables/schemas or belong to the migration role.

Create non-owner group roles once as a DBA:

```sql
CREATE ROLE retail_order_app NOLOGIN;
CREATE ROLE retail_payment_app NOLOGIN;
CREATE ROLE retail_inventory_app NOLOGIN;
```

Create separate service login credentials through your normal secret provisioning
process, grant only the corresponding group role, and grant database CONNECT.
Do not grant those roles CREATE on the database, CREATE on service schemas,
superuser, BYPASSRLS, or memberships in other service/migration roles. Audit
inherited privileges and existing PUBLIC grants on databases/public schemas;
the service-schema grants alone cannot undo privileges from an existing cluster.

After migrations, run `database/schema/runtime-grants.sql` as the migration owner
or DBA. It grants schema USAGE and access to named tables only. Runtime deletes
are not granted. Payment transaction rows permit SELECT/INSERT only; a trigger
also rejects UPDATE/DELETE. Database administrators can bypass triggers or
truncate tables, so this is not a tamper-proof external audit system. Retention,
archiving, backups, and disaster recovery need separate operational procedures.

The notification service has no persisted data or database login in this phase.
It will consume events; durable inbox/delivery history can be introduced through
a separately owned schema if requirements call for it.

Migrations and validation are documented in [database setup](../../database/README.md).
