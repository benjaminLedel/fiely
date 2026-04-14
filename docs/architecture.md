# Fiely — Architecture

This document describes the technical architecture of Fiely. It is a living document and will evolve as the project matures.

---

## Overview

Fiely follows a **modular monolith** approach for the backend — structured enough to scale, simple enough to self-host on a single machine. The frontend is a React SPA, and mobile/desktop clients are built with Flutter.

```
┌─────────────────────────────────────────────────────────┐
│                        Clients                          │
│          React Web App      Flutter Mobile/Desktop      │
└────────────────────┬────────────────────────────────────┘
                     │ REST / WebSocket
┌────────────────────▼────────────────────────────────────┐
│                   Spring Boot API                        │
│                                                         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐ │
│  │   File API  │  │  Share API  │  │     AI API      │ │
│  └──────┬──────┘  └──────┬──────┘  └────────┬────────┘ │
│         │                │                   │          │
│  ┌──────▼──────────────────────────────────▼─────────┐  │
│  │               Core Services                       │  │
│  │  FileService  ShareService  AIService  AuthService│  │
│  └──────┬───────────────────────────────┬────────────┘  │
└─────────┼───────────────────────────────┼───────────────┘
          │                               │
┌─────────▼──────────┐       ┌────────────▼───────────────┐
│   File Storage     │       │       PostgreSQL            │
│  (local filesystem)│       │   + pgvector extension      │
└────────────────────┘       └────────────────────────────┘
```

---

## Backend — Spring Boot (Kotlin)

The backend is a Spring Boot application written in **Kotlin**, organized into modules by domain. It uses a [PF4J-based plugin architecture](plugin-architecture.md) to make AI providers, storage backends, authentication methods, and third-party apps pluggable.

### Module Structure

```
fiely-backend/                          # Gradle multi-project build
├── fiely-plugin-api/                   # Shared interfaces + DTOs (Kotlin, no Spring)
│   └── src/main/kotlin/cloud/fiely/plugin/
│       ├── AIProvider.kt
│       ├── StorageProvider.kt
│       ├── AuthProvider.kt
│       ├── FileProcessor.kt
│       ├── NotificationProvider.kt
│       └── FielyApp.kt
│
├── fiely-core/                         # Spring Boot application
│   └── src/main/kotlin/cloud/fiely/
│       ├── files/         # File management, chunked upload
│       ├── sharing/       # Share links, guest access
│       ├── users/         # User and team management
│       ├── plugin/        # PF4J Plugin Manager, event bridge
│       └── common/        # Shared utilities, config
│
└── plugins/                            # First-party plugins (monorepo)
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

### Key Design Decisions

**Plugin Architecture (PF4J)**
Core functionality (storage, auth, AI, file processing, notifications) is defined as extension point interfaces in `fiely-plugin-api`. Plugins are separate JARs loaded at runtime. See [Plugin Architecture](plugin-architecture.md) for details.

**Chunked Upload via tus.io**
All file uploads go through the [tus protocol](https://tus.io). This gives us resume support, progress tracking, and large file handling out of the box. The Spring Boot tus server implementation handles chunk assembly before writing to the file storage.

**Storage Abstraction**
The `StorageProvider` interface abstracts file storage so that different backends (local filesystem, S3-compatible services) can be swapped in via configuration — no code changes required. The built-in implementation uses the local filesystem.

**AI Provider Abstraction**
The `AIProvider` interface allows pluggable AI backends:

```kotlin
interface AIProvider : ExtensionPoint {
    val id: String
    val displayName: String
    fun embed(text: String): List<Float>
    fun complete(prompt: String): String
    fun chat(messages: List<Message>): Flow<String>
}
```

Implementations ship as plugins: `fiely-ai-ollama`, `fiely-ai-openai`, `fiely-ai-claude`. Configured via `application.yml`.

---

## Database — PostgreSQL + pgvector

PostgreSQL is the primary database. The `pgvector` extension enables vector similarity search for semantic file search.

### Key Tables

| Table | Purpose |
|---|---|
| `users` | User accounts |
| `teams` | Team / organization workspaces |
| `files` | File metadata (name, path, size, mime type, owner) |
| `file_versions` | Version history per file |
| `shares` | Share links and guest access |
| `file_embeddings` | Vector embeddings for semantic search |
| `audit_logs` | Audit trail for enterprise compliance |
| `tags` | AI-generated and manual tags per file |

---

## File Storage

Fiely stores uploaded files on the local filesystem. The storage location is configurable via `application.yml`.

Files are stored with the following directory structure:

```
{tenant_id}/{user_id}/{file_id}/{version}
```

Metadata (filename, path, permissions) is stored in PostgreSQL. The filesystem only holds raw bytes.

---

## AI Architecture

AI features are **opt-in** and **provider-agnostic**.

### Supported Providers

| Provider | Use Case |
|---|---|
| Ollama | Local, fully private, no API key needed |
| OpenAI | Cloud, GPT-4o recommended |
| Anthropic Claude | Cloud, excellent for document understanding |

### AI Features

**Semantic Search**
On file upload, text content is extracted (PDF, DOCX, TXT, etc.) and passed to the embedding model. Vectors are stored in `file_embeddings` via pgvector. Search queries are also embedded and compared via cosine similarity.

**Auto-Tagging**
After upload, a classification prompt is sent to the configured LLM. The model returns structured tags (document type, topic, language). Tags are stored and shown in the UI.

**Chat with Files (RAG)**
User selects one or more files → content is chunked and retrieved via similarity search → passed as context to the LLM → response is streamed back to the client.

**Content Generation**
Generate summaries, draft emails, or create reports based on file content. Uses the same RAG pipeline with a different system prompt.

---

## Frontend — React

The web frontend is a React SPA built with:

- **React 18** with TypeScript
- **TanStack Query** for server state
- **Zustand** for client state
- **Tailwind CSS** for styling
- **tus-js-client** for chunked uploads

### Key Views

```
/                    → Dashboard / Recent files
/files/:path         → File browser
/shared/:token       → Public share view (no auth required)
/search              → Semantic search
/ai/chat             → Chat with files
/settings            → User and team settings
/admin               → Admin panel (enterprise)
```

---

## Mobile & Desktop — Flutter

The Flutter app targets iOS, Android, and Desktop (Windows, macOS, Linux).

### Architecture
Flutter app follows a clean architecture pattern:

```
lib/
├── data/          # API clients, local storage
├── domain/        # Business logic, models
├── presentation/  # UI screens and widgets
└── core/          # Config, routing, theme
```

Phase 1 ships **web + mobile only** — no desktop sync client yet. Desktop sync (à la Dropbox) is a Phase 3 goal.

---

## Authentication

Fiely uses **JWT-based authentication** with optional OIDC/SSO integration.

| Mode | Use Case |
|---|---|
| Built-in auth | Default, no external dependency |
| OIDC / OAuth2 | Google, GitHub, custom provider |
| SAML / Keycloak | Enterprise SSO |
| LDAP / Active Directory | Enterprise user sync |

---

## Multi-Tenancy

Fiely supports multiple organizations on a single instance. Tenants are isolated at the database and storage level:

- Each tenant has its own storage directory
- Row-level security in PostgreSQL ensures cross-tenant data leakage is impossible
- Tenant-specific AI provider configuration

---

## Deployment

Fiely is designed to run in Docker. A single `docker-compose.yml` brings up the full stack:

```yaml
services:
  fiely-api:      # Spring Boot backend
  fiely-web:      # React frontend (nginx)
  postgres:       # PostgreSQL + pgvector
  ollama:         # Local AI (optional)
```

For production, each service can be deployed independently. Kubernetes manifests will be provided in a future release.

---

## Security Considerations

- All file access is permission-checked at the service layer — no direct storage URLs exposed to clients
- Share tokens are cryptographically random (256-bit)
- Passwords are hashed with bcrypt
- Audit logs are append-only
- AI providers never receive raw file content without user consent
- No telemetry, no analytics, no phoning home

---

## Contributing to Architecture

Have a suggestion for the architecture? Open an issue with the label `architecture` and let's discuss before implementing. Major architectural decisions will be documented here as Architecture Decision Records (ADRs) in `docs/adr/`.