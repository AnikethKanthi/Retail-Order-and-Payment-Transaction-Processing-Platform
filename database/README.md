# Database

PostgreSQL 17 is the initial target. The versioned SQL in `migrations/` is the
authoritative schema; do not maintain a second executable schema dump.

See [database design](../docs/database-design.md) for entity relationships, service
ownership, constraints, monetary precision, and application-level invariants.

## Apply migrations

Prerequisites: JDK 21+, the existing backend Maven wrapper, a running PostgreSQL
17 instance, and an empty UTF-8 database owned by a dedicated migration login.
The migration login needs database CREATE and ownership of the created schemas.
It must not be used by an application. Database/login creation is an operator
step; migrations never create databases, roles, passwords, or sample customers.

Set `FLYWAY_URL`, `FLYWAY_USER`, and `FLYWAY_PASSWORD` in the current shell, using
the names in [`.env.example`](../.env.example). Do not pass real passwords in
command-line arguments or commit them. Maven does not automatically load `.env`.

From the repository root in PowerShell:

```powershell
.\backend\mvnw.cmd -f database/pom.xml flyway:info
.\backend\mvnw.cmd -f database/pom.xml flyway:migrate
.\backend\mvnw.cmd -f database/pom.xml flyway:validate
```

Linux/macOS: replace `.\backend\mvnw.cmd` with `sh ./backend/mvnw`.
The Maven POM inherits Flyway and PostgreSQL JDBC versions from the backend's
Spring Boot dependency management. It is deliberately outside the backend reactor
and has no lifecycle-bound migration execution. Ordinary `clean verify` builds do
not connect to a database. Spring Boot runtime database integrations remain future
work; there is no Hibernate schema generation or per-service migration runner yet.

Flyway records checksums in `platform_migrations.flyway_schema_history`. It creates
that history schema automatically; V1 creates the three service schemas. The
initial migration assumes those service schemas do not already exist. Migration
history is shared for this initial single-database deployment; logical ownership
still belongs to each service. Serialize production schema deployments through one
migration job. Before separate service databases are deployed, split migration
locations/history in a deliberate deployment change; do not copy one history
blindly across databases.

## Migration conventions

- Name new files `V5__description.sql`, `V6__description.sql`, and so on, using
  globally increasing versions. Never edit an already applied migration.
- Keep SQL schema-qualified and transactional. These initial migrations contain
  no nontransactional statements; each migration is atomic in PostgreSQL.
- `validateOnMigrate` is enabled. Automatic baseline and out-of-order execution
  are disabled; Flyway `clean` is disabled to prevent accidental data deletion.
- Use forward corrective migrations and backup/restore for recovery. There are
  no destructive automatic rollback scripts. Review expand/contract changes and
  data backfills separately before production use.
- `seed-data/` remains empty except for `.gitkeep`. Never auto-load development
  seed data in production migrations.

## Runtime privileges and verification

Have the DBA provision separate non-owner roles/logins as described in
[PostgreSQL operations](../infrastructure/postgres/README.md), then explicitly
apply [`schema/runtime-grants.sql`](schema/runtime-grants.sql). It is an operator
script, not a Flyway migration. No runtime role receives cross-service access or
DDL privileges from this setup. New tables need explicit grants.

Run [database integration checks](../tests/integration/database/README.md) against
a disposable migrated database. They exercise valid inserts, generated totals,
invalid data rejection, foreign keys, uniqueness, and append-only financial rows.
