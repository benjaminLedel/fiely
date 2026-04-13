# fiely-backend

Spring Boot backend for [Fiely](../README.md). Modular monolith with package base `cloud.fiely`, built on Java 21 and Gradle.

See [`docs/architecture.md`](../docs/architecture.md) for the overall design and [`CONTRIBUTING.md`](../CONTRIBUTING.md) for contribution rules.

## Prerequisites

- Java 21+
- A running PostgreSQL 15+ (or override `FIELY_DB_*` to point elsewhere)

## Run

```bash
./gradlew bootRun
```

The application starts on `http://localhost:8080`.

### Environment variables

| Variable            | Default                                  | Description             |
|---------------------|------------------------------------------|-------------------------|
| `FIELY_DB_URL`      | `jdbc:postgresql://localhost:5432/fiely` | JDBC URL                |
| `FIELY_DB_USER`     | `fiely`                                  | Database user           |
| `FIELY_DB_PASSWORD` | `fiely`                                  | Database password       |

### Dev profile

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

Enables SQL logging and `cloud.fiely` debug logs.

## Test

```bash
./gradlew test
```

Tests run against an in-memory H2 database in PostgreSQL compatibility mode; Flyway is disabled in the test profile. Integration tests against a real PostgreSQL instance (via Testcontainers) will be added with the feature modules.

## Smoke test

```bash
curl -s http://localhost:8080/api/ping
# {"status":"ok","app":"fiely"}

curl -s http://localhost:8080/actuator/health
# {"status":"UP", ...}
```

## Module layout

```
src/main/java/cloud/fiely/
├── FielyApplication.java
├── auth/      # Authentication, JWT, OIDC       (placeholder)
├── files/     # File management, chunked upload (placeholder)
├── sharing/   # Share links, guest access       (placeholder)
├── users/     # User and team management        (placeholder)
├── ai/        # AI provider abstraction         (placeholder)
├── storage/   # File storage abstraction         (placeholder)
└── common/    # Shared utilities, web, config
```

Each feature module owns its own controllers, services, repositories, and migrations. Cross-module access goes through public API types in `common/` or published ports in the owning module.

## Database migrations

Flyway migrations live under `src/main/resources/db/migration/`. Naming follows `V<n>__<description>.sql`. `V1__init.sql` is the baseline migration.
