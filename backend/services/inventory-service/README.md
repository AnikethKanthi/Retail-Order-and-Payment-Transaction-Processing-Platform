# Inventory Service

Maven module: `com.aniketh.retail:inventory-service`.

This initialization contains a module descriptor and baseline configuration only.
There is no application entry point or business logic yet. The module builds as
an ordinary JAR; it is not an executable Spring Boot application at this stage.

- Configuration: `src/main/resources/application.yml`.
- Default future HTTP port: `8083`; override with `SERVER_PORT`.
- Java package convention: `com.aniketh.retail.inventory`.
- Build from `backend/`: `sh ./mvnw -pl services/inventory-service -am clean verify`
  (Windows: `mvnw.cmd`).
- See [backend setup](../../README.md) for prerequisites and conventions.

TODO: Add an application entry point, activate `spring-boot-maven-plugin` in this
module's build plugins, and implement the service in a later phase.
