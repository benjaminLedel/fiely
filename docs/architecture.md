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
│     MinIO          │       │       PostgreSQL            │
│  (File Storage)    │       │   + pgvector extension      │
└────────────────────┘       └────────────────────────────┘
```

---

## Backend — Spring Boot

The backend is a single Spring Boot application, organized into modules by domain.

### Module Structure

```
fiely-backend/
├── src/main/java/cloud/fiely/
│   ├── auth/          # Authentication, JWT, OIDC
│   ├── files/         # File management, chunked upload
│   ├── sharing/       # Share links, guest access
│   ├── users/         # User and team management
│   ├── ai/            # AI provider abstraction + features
│   ├── storage/       # MinIO abstraction layer
│   └── common/        # Shared utilities, config
```

### Key Design Decisions

**Chunked Upload via tus.io**
All file uploads go through the [tus protocol](https://tus.io). This gives us resume support, progress tracking, and large file handling out of the box. The Spring Boot tus server implementation handles chunk assembly before writing to MinIO.

**Storage Abstraction**
The `StorageProvider` interface abstracts MinIO so that other S3-compatible backends (AWS S3, Hetzner Object Storage, local filesystem) can be swapped in via configuration — no code changes required.

**AI Provider Abstraction**
The `AIProvider` interface allows pluggable AI backends:

```java
public interface AIProvider {
    List<Float> embed(String text);
    String complete(String prompt);
    String chat(List<Message> messages);
}
```

Implementations: `OllamaProvider`, `OpenAIProvider`, `ClaudeProvider`. Configured via `application.yml`.

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

## File Storage — MinIO

Fiely uses MinIO as its object storage backend. MinIO is S3-compatible, self-hostable, and performant.

Files are stored with the following key structure:

```
{tenant_id}/{user_id}/{file_id}/{version}
```

Metadata (filename, path, permissions) is stored in PostgreSQL. MinIO only holds raw bytes.

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

- Each tenant has its own storage prefix in MinIO
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
  minio:          # Object storage
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