# fiely-backend

Spring Boot backend for [Fiely](../README.md). Written in **Kotlin**, built with Gradle, using a [PF4J-based plugin architecture](../docs/plugin-architecture.md).

See [`docs/architecture.md`](../docs/architecture.md) for the overall design and [`CONTRIBUTING.md`](../CONTRIBUTING.md) for contribution rules.

## Prerequisites

- Java 21+ (JVM)
- A running PostgreSQL 15+ (or override `FIELY_DB_*` to point elsewhere)

## Run

```bash
./gradlew :fiely-core:bootRun
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
SPRING_PROFILES_ACTIVE=dev ./gradlew :fiely-core:bootRun
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

## API documentation

Fiely ships with [springdoc-openapi](https://springdoc.org/). Once the application is running:

- **OpenAPI 3 spec (JSON):** <http://localhost:8080/v3/api-docs>
- **OpenAPI 3 spec (YAML):** <http://localhost:8080/v3/api-docs.yaml>
- **Swagger UI:** <http://localhost:8080/swagger-ui.html>

The spec is generated from `@RestController` classes at runtime, so endpoints contributed by loaded plugins show up automatically. See `cloud.fiely.common.web.OpenApiConfig` for the top-level metadata and the shared `bearerAuth` security scheme.

## Project structure

```
fiely-backend/                          # Gradle multi-project build
├── fiely-plugin-api/                   # Shared interfaces + DTOs (Kotlin, no Spring)
│   └── src/main/kotlin/cloud/fiely/plugin/
│
├── fiely-core/                         # Spring Boot application
│   └── src/main/kotlin/cloud/fiely/
│       ├── files/         # File management, chunked upload
│       ├── sharing/       # Share links, guest access
│       ├── users/         # User and team management
│       ├── plugin/        # PF4J Plugin Manager, event bridge
│       └── common/        # Shared utilities, config
│
└── plugins/                            # First-party plugins
    ├── fiely-auth-jwt/                 # JWT / database auth (default)
    ├── fiely-auth-oidc/                # OIDC / Keycloak
    ├── fiely-auth-ldap/                # LDAP / Active Directory
    ├── fiely-storage-local/            # Local filesystem (default)
    ├── fiely-ai-ollama/                # Ollama (local AI)
    ├── fiely-ai-openai/                # OpenAI API
    ├── fiely-ai-claude/                # Anthropic Claude API
    ├── fiely-processor-text/           # Text extraction (PDF, DOCX)
    └── fiely-notify-email/             # E-Mail notifications
```

Each plugin is a standalone Gradle submodule that depends on `fiely-plugin-api` (compileOnly). See [Plugin Architecture](../docs/plugin-architecture.md) for details.

## Database migrations

Flyway migrations live under `fiely-core/src/main/resources/db/migration/`. Naming follows `V<n>__<description>.sql`. `V1__init.sql` is the baseline migration.
