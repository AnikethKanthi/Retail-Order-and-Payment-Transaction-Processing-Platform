# Shared Backend Components

Maven artifact: `com.aniketh.retail:retail-shared`.

This is a plain Java 21 library JAR inherited from the backend parent. It currently
contains no Java sources or dependencies. All four services depend on this module;
its version is managed in the parent POM.

Future shared contracts and utilities belong under `com.aniketh.retail.shared`.
Keep service-specific business logic and persistence in their service modules.
Do not activate the Spring Boot repackage plugin here.

Build from `backend/`: `sh ./mvnw -pl shared -am clean verify`
(PowerShell: `.\mvnw.cmd -pl shared -am clean verify`).

See [backend setup](../README.md) for build prerequisites and code conventions.
