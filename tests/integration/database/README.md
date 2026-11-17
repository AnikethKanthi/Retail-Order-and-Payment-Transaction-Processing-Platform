# Database Integration Checks

Use a disposable PostgreSQL 17 database after applying all Flyway migrations. Do
not point these checks at production; they insert fixtures and deliberately try
invalid operations. Everything in `constraints.sql` runs inside a transaction
that is rolled back. Run with the migration owner so permission errors do not
mask constraint failures.

With PostgreSQL's `psql` installed, set your normal `PGHOST`, `PGPORT`, `PGDATABASE`,
`PGUSER`, and secure password configuration, then from the repository root:

```sh
psql -X -v ON_ERROR_STOP=1 -f tests/integration/database/constraints.sql
```

The file tests a valid complete data path, generated totals and available stock,
case-insensitive customer uniqueness, same-service foreign keys, duplicate keys,
invalid statuses/currencies, quantities/amounts, overflow, NaN, and append-only
financial operations. An unexpected SQLSTATE or accepted invalid statement fails
the run. Application workflows and concurrent service updates are not covered.

Also verify Flyway behavior on the disposable database:

1. Run `flyway:migrate` on the empty database; V1 through V4 must succeed.
2. Run `flyway:migrate` again; no migration should run a second time.
3. Run `flyway:validate`; stored checksums must match.
4. Provision runtime group roles and apply `database/schema/runtime-grants.sql`.
   Run `psql -X -v ON_ERROR_STOP=1 -f tests/integration/database/privileges.sql`.
   It checks that each role can access only its own tables, that no role has DDL
   or DELETE, and that payment transaction UPDATE is denied.

See [the migration runbook](../../../database/README.md) for wrapper commands.
