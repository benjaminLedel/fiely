# Contributing to Fiely

First off — thank you for considering contributing to Fiely. 🎉

Fiely is in early design phase, which means your input right now has a huge impact on the direction of the project. Whether you're fixing a typo, proposing an architecture decision, or building a major feature — all contributions are welcome.

---

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
- [Development Setup](#development-setup)
- [Project Structure](#project-structure)
- [Commit Convention](#commit-convention)
- [Pull Request Process](#pull-request-process)
- [Design Principles](#design-principles)

---

## Code of Conduct

This project is a welcoming space for everyone. We expect contributors to:

- Be respectful and constructive in discussions
- Welcome differing opinions and experiences
- Focus on what is best for the community and the project

Unacceptable behavior will not be tolerated. If you experience issues, please open a private issue or contact the maintainer directly.

---

## How Can I Contribute?

### 🐛 Reporting Bugs

Open an issue and include:
- A clear description of the bug
- Steps to reproduce
- Expected vs. actual behavior
- Your environment (OS, browser, Docker version, etc.)

Use the label `bug`.

### 💡 Suggesting Features

Open an issue with the label `enhancement`. Describe:
- What problem does this solve?
- Who benefits from this feature?
- Any ideas on how it could be implemented?

We especially welcome input on:
- AI feature ideas
- Enterprise / public sector requirements
- UX improvements
- Self-hosting ergonomics

### 📖 Improving Documentation

Docs are just as important as code. If something is unclear, incomplete, or missing — please fix it. Documentation PRs are always welcome and don't require any setup.

### 🛠️ Contributing Code

See [Development Setup](#development-setup) below. Before starting work on a larger feature, please open an issue first so we can discuss the approach.

---

## Development Setup

> ⚠️ Fiely is in early phase. The full dev setup guide will be added as the codebase is established. For now, this is the intended setup.

### Prerequisites

- Java 21+
- Node.js 20+
- Flutter 3.x
- Docker + Docker Compose
- Git

### Getting Started

```bash
# Clone the repo
git clone https://github.com/benjaminLedel/fiely
cd fiely

# Start dependencies (PostgreSQL, MinIO, Ollama)
docker compose -f docker-compose.dev.yml up -d

# Backend
cd fiely-backend
./mvnw spring-boot:run

# Frontend
cd fiely-web
npm install
npm run dev

# Mobile
cd fiely-mobile
flutter pub get
flutter run
```

### Environment Variables

Copy `.env.example` to `.env` and adjust to your local setup. Never commit `.env` files.

---

## Project Structure

```
fiely/
├── fiely-backend/      # Spring Boot API
├── fiely-web/          # React frontend
├── fiely-mobile/       # Flutter mobile + desktop app
├── fiely-docs/         # Documentation site (planned)
├── docs/               # Architecture docs, ADRs
│   └── adr/            # Architecture Decision Records
├── docker-compose.yml          # Production compose
├── docker-compose.dev.yml      # Development compose
└── README.md
```

---

## Commit Convention

We use [Conventional Commits](https://www.conventionalcommits.org/).

```
<type>(<scope>): <short description>

Types:
  feat      New feature
  fix       Bug fix
  docs      Documentation only
  style     Formatting, missing semicolons, etc.
  refactor  Code change that is neither a fix nor a feature
  test      Adding or fixing tests
  chore     Build process, dependencies, tooling

Examples:
  feat(files): add chunked upload via tus.io
  fix(sharing): expire share tokens correctly
  docs(arch): add pgvector design decision
  chore(deps): update spring boot to 3.4.0
```

---

## Pull Request Process

1. **Fork** the repository and create a branch from `main`
2. Branch naming: `feat/your-feature`, `fix/your-bug`, `docs/your-change`
3. Make your changes with clear, focused commits
4. **Write or update tests** where applicable
5. Make sure the project builds and tests pass locally
6. Open a Pull Request with:
   - A clear title following the commit convention
   - Description of what changed and why
   - Reference to the related issue (`Closes #123`)
7. A maintainer will review and give feedback
8. Once approved, your PR will be merged

### PR Checklist

- [ ] My code follows the project's style and design principles
- [ ] I have added/updated tests where applicable
- [ ] I have updated documentation if needed
- [ ] My changes don't introduce unnecessary dependencies
- [ ] I have tested my changes locally

---

## Design Principles

When contributing, please keep these principles in mind:

**Simple over complex**
If a feature can be implemented in a simpler way, choose that. Avoid configuration explosion — Nextcloud's settings hell is what we're moving away from.

**Privacy first**
No telemetry. No tracking. No external calls without explicit user action. AI features must be transparent about what data is sent where.

**Self-hosting friendly**
Every feature must work on a single Docker Compose setup on a €5 VPS. Enterprise features are additive, never required for the core experience.

**AI as enhancement**
AI features must degrade gracefully when no AI provider is configured. The app must be fully functional without AI.

**Open standards**
Prefer open protocols and formats. WebDAV, S3, OIDC, tus — these are battle-tested and interoperable.

---

## Questions?

Open an issue with the label `question` or start a discussion in the GitHub Discussions tab.

We're building this in public and love hearing from the community. 🚀