# 🗂️ Fiely

**Your files. Your rules. Your AI.**

Fiely is a modern, open-source alternative to Nextcloud — built for teams, schools, organizations, and anyone who believes their data should stay in their control. No PHP. No bloat. No compromises.

> ⚠️ Fiely is in early design phase. We're building in public. Stars, feedback, and contributions are welcome.

---

## Why Fiely?

Nextcloud is powerful — but it's showing its age. A complex PHP stack, an overwhelming UI, and AI features bolted on as an afterthought. SharePoint is Microsoft. Google Drive is not DSGVO-compliant.

**There has to be a better way.**

Fiely is built from the ground up with a modern stack, a clean UX, and AI that's integrated where it makes sense — not forced where it doesn't.

---

## Who is Fiely for?

- 👨‍👩‍👧 **Families** who want a private photo and file storage without Big Tech
- 🏢 **Small teams** who need secure file sharing and collaboration
- 🏫 **Schools and educational institutions** looking for a DSGVO-compliant Nextcloud alternative
- 🏛️ **Public sector organizations** that require on-premise, auditable, and BSI-compatible solutions
- 🧑‍💻 **Self-hosters** who care about their data sovereignty

---

## Core Features (Planned)

### 📁 File Management
- Upload, organize, and manage files and folders
- Chunked uploads with resume support (powered by [tus.io](https://tus.io))
- Versioning and trash recovery
- Granular permission and role management

### 🔗 Sharing
- Share files and folders with public links
- Guest access with optional password protection
- Expiring share links
- Team and organization workspaces

### 🤖 AI — Where It Makes Sense
AI is not the product. AI makes the product better.

- **Semantic Search** — Find files by content, not just filename. "Show me the contract from last March" just works.
- **Auto-Tagging** — Upload a PDF, Fiely understands what it is. Invoice, contract, photo, code — automatically categorized.
- **Chat with Your Files** — Ask questions across your documents. RAG-powered, privacy-first.
- **Content Generation** — Draft emails, summaries, or reports based on your stored files.

AI providers are **pluggable** — run locally with [Ollama](https://ollama.com) or connect to Claude / OpenAI. You choose. You can also run Fiely with AI completely disabled.

### 🏢 Enterprise & Public Sector Ready
- LDAP / Active Directory integration
- SSO / SAML support (Keycloak compatible)
- Audit logs
- Multi-tenancy
- On-premise deployment (no cloud dependency)
- DSGVO / GDPR compliant by design

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot (Kotlin) |
| Frontend | React |
| Mobile & Desktop | Flutter |
| Storage | Local filesystem |
| Database | PostgreSQL + pgvector |
| Chunked Upload | tus.io |
| AI (local) | Ollama |
| AI (cloud) | OpenAI / Claude API (pluggable) |
| Auth | Keycloak / OIDC |

---

## Design Philosophy

- **Simple over complex** — If Nextcloud has a setting for it, we ask: does it need to be a setting?
- **Privacy first** — No telemetry. No phoning home. Your data stays where you put it.
- **AI as enhancement** — AI features are opt-in and transparent. No black boxes.
- **Open by default** — Open source, open standards, open APIs.

---

## Roadmap

### 🌱 Phase 1 — Foundation
- [ ] Core file management (upload, download, folders)
- [ ] User management and authentication
- [ ] Basic sharing with public links
- [ ] React web UI
- [ ] Docker deployment

### 🌿 Phase 2 — Mobile & AI
- [ ] Flutter mobile app (iOS + Android)
- [ ] Semantic search with pgvector
- [ ] Auto-tagging
- [ ] Ollama integration

### 🌳 Phase 3 — Enterprise
- [ ] LDAP / SSO integration
- [ ] Multi-tenancy
- [ ] Audit logs
- [ ] Chat with files (RAG)
- [ ] Enterprise admin dashboard

---

## Self-Hosting

Fiely ships as a **single container** — backend + frontend baked into one
image. The root `Dockerfile` is a 3-stage build (Node for the frontend,
Gradle for the backend, slim JRE for the runtime) that embeds the built
React assets into Spring Boot's `static/` resources. An SPA fallback
ensures deep links work on refresh.

### Quick start (Docker Compose)

```bash
git clone https://github.com/benjaminledel/fiely
cd fiely
docker compose up
```

That's it. The compose file **pulls the official image from GHCR**
(`ghcr.io/benjaminledel/fiely:latest`, published by CI) and starts:

- **PostgreSQL 16 + pgvector** (for future semantic search) with a named
  volume so data persists across restarts
- **Fiely** on http://localhost:8080

The API is available under `/api`, the actuator under `/actuator`, and
everything else falls through to the React SPA.

If the image can't be pulled (new repo, no network, offline dev) compose
falls back to a local build from the root `Dockerfile` — no extra flags
needed.

```bash
docker compose pull            # refresh to the latest published image
docker compose up --build      # force a build from your local checkout
docker compose down            # stop, keep data
docker compose down -v         # stop and delete the postgres volume
```

Copy `.env.example` to `.env` to pin a specific image tag, override the
port, or change database credentials. Defaults are safe for local-only dev
— **change the password before exposing Fiely to anything real**.

### Build the image manually

If you don't want compose:

```bash
docker build -t fiely .
docker run --rm -p 8080:8080 \
  -e FIELY_DB_URL=jdbc:postgresql://host.docker.internal:5432/fiely \
  -e FIELY_DB_USER=fiely \
  -e FIELY_DB_PASSWORD=fiely \
  fiely
```

### Development (HMR for the frontend)

There's a separate dev overlay that adds a Vite dev server with hot
module reload and forces a local build of the backend:

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up
```

| Service | URL | Purpose |
|---|---|---|
| `fiely-frontend-dev` | http://localhost:5173 | Vite dev server, HMR on save |
| `fiely` | http://localhost:8080 | Backend (API + baked frontend, unused in dev) |
| `postgres` | — | PostgreSQL 16 + pgvector |

Open **http://localhost:5173** — Vite proxies `/api` and `/actuator` to
the backend container, so the frontend talks to a real backend without
CORS hassle. Edits under `fiely-frontend/src/` hot-reload instantly.

Backend code changes still need `docker compose build fiely` + `up -d`;
Spring DevTools-style restart can be enabled by uncommenting the env var
in `docker-compose.dev.yml`.

---

## Contributing

Fiely is in early design phase and we'd love your input.

- 💬 Open an issue to discuss features or ideas
- ⭐ Star the repo to follow progress
- 🛠️ PRs welcome once we have a contribution guide

---

## License

Fiely is open source under the **MIT License**.

A future Enterprise Edition with additional features for large organizations is planned, following the open-core model.

---

## Philosophy

> *"Your files belong to you. Not to a corporation, not to a cloud provider, not to us. Fiely is just the tool that helps you keep it that way."*

---

**fiely.cloud** · Built with ❤️ in Germany · DSGVO compliant by design
