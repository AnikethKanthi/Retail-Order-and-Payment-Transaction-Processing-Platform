# Notification Service

Maven module: `com.aniketh.retail:notification-service`.

Has no persisted tables or database role in this phase. Consume events rather
than querying other service schemas. See
[database ownership](../../../docs/database-design.md).

This initialization contains a module descriptor and baseline configuration only.
There is no application entry point or business logic yet. The module builds as
an ordinary JAR; it is not an executable Spring Boot application at this stage.

- Configuration: `src/main/resources/application.yml`.
- Default future HTTP port: `8084`; override with `SERVER_PORT`.
- Java package convention: `com.aniketh.retail.notification`.
- Build from `backend/`: `sh ./mvnw -pl services/notification-service -am clean verify`
  (Windows: `mvnw.cmd`).
- See [backend setup](../../README.md) for prerequisites and conventions.

TODO: Add an application entry point, activate `spring-boot-maven-plugin` in this
module's build plugins, and implement the service in a later phase.
