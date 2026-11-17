# Retail-Order-and-Payment-Transaction-Processing-Platform

The backend Maven multi-module build is initialized with Java 21, Spring Boot
3.5.16, four service modules, and a shared library. Application implementation and
the remaining infrastructure/frontend setup are planned for later phases.

See [backend setup and conventions](backend/README.md) for prerequisites, module
details, and configuration placeholders.

Build the backend from the repository root in PowerShell:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml clean verify
```

On Linux/macOS: `sh ./backend/mvnw -f backend/pom.xml clean verify`.

PostgreSQL structures, constraints, service ownership, and standalone Flyway
migrations are documented in [database design](docs/database-design.md) and the
[migration runbook](database/README.md). Migrations are run explicitly against a
configured database; backend builds do not apply them.
