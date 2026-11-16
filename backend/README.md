# Backend

The backend is a Maven reactor with a parent POM, four service modules, and one
shared library. This phase initializes builds and configuration only; there are
no Java sources, business features, or runnable applications yet.

## Prerequisites

- JDK 21 or newer, with `JAVA_HOME` pointing to the JDK. Compilation targets Java 21.
- The Maven wrapper pins Maven 3.9.16. A standalone Maven 3.9.9 or newer also works.
- Internet access on the first build to download Maven and dependencies.
- Spring Boot 3.5.16, configured centrally through `spring-boot-starter-parent`.

Check `java -version` and `sh ./mvnw -version` (Windows: `mvnw.cmd -version`). A Java 8
runtime is insufficient. No database, Kafka, Redis, or Docker is needed to build.

## Build

From `backend/`, in PowerShell:

```powershell
.\mvnw.cmd clean verify
```

On Linux or macOS:

```sh
sh ./mvnw clean verify
```

From the repository root in PowerShell:

```powershell
.\backend\mvnw.cmd -f backend/pom.xml clean verify
```

Build a service and its reactor dependencies:

```powershell
.\mvnw.cmd -pl services/order-service -am clean verify
```

The reactor builds the shared JAR before dependent services. Service JARs currently
contain baseline resources only; an empty shared JAR is expected. `verify` checks
the build lifecycle, but there are no automated tests or application behaviors to
validate yet. Add tests with their implementations in later phases.

## Modules and dependency management

| Module | Artifact | Future default HTTP port |
| --- | --- | --- |
| `shared` | `retail-shared` | N/A |
| `services/order-service` | `order-service` | 8081 |
| `services/payment-service` | `payment-service` | 8082 |
| `services/inventory-service` | `inventory-service` | 8083 |
| `services/notification-service` | `notification-service` | 8084 |

All modules inherit `com.aniketh.retail:retail-order-payment-parent:0.1.0-SNAPSHOT`.
Spring Boot's parent manages compatible third-party dependencies and standard
compiler/test plugin versions. The backend parent manages the shared library
version. Child POMs omit dependency versions; add future version overrides in the
parent instead of repeating them across services.

Services declare Spring Web, Validation, Actuator, and test starters. Database,
Kafka, and Redis dependencies will be added when their integrations are designed.
The Boot Maven plugin is managed centrally but intentionally not activated yet.
When an application main class is added, declare the plugin in that service's
`build/plugins` to enable executable JAR packaging. Keep `retail-shared` a normal
library JAR without Boot repackaging.

## Conventions

- Use Maven's `src/main/java`, `src/main/resources`, `src/test/java`, and
  `src/test/resources` layout as files are added.
- Use base packages `com.aniketh.retail.order`, `.payment`, `.inventory`,
  `.notification`, and `.shared` respectively.
- Use `UpperCamelCase` for types, `lowerCamelCase` for methods and fields,
  `UPPER_SNAKE_CASE` for constants, and a `Test` suffix for unit test classes.
- Follow the root `.editorconfig`: UTF-8, LF, four spaces for Java/XML and two
  spaces for YAML/JSON. Use an EditorConfig-aware editor. These style conventions
  are documented/editor-assisted; no Java style checker is installed yet.
- Build prerequisites are enforced in every module during Maven validation.
- Keep shared code limited to deliberately shared contracts/utilities; avoid
  service-specific persistence and business behavior in the shared module.

## Configuration placeholders

Each service has an `application.yml` containing its name, a default port, and
TODOs for infrastructure configuration. `SERVER_PORT` overrides the default port.
Future secrets must come from environment variables or an untracked
`application-local.yml`, selected explicitly with the `local` Spring profile.
No local profile is activated by default. Spring Boot does not automatically load
the repository's `.env` file; export variables in the shell or configure the IDE.
