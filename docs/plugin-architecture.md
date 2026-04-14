# Fiely — Plugin Architecture

This document describes the plugin architecture for Fiely. The goal is to make AI providers, storage backends, authentication methods, and third-party apps pluggable — without compromising simplicity for self-hosters.

---

## Overview

Fiely uses [PF4J](https://github.com/pf4j/pf4j) (Plugin Framework for Java) with Spring Boot integration as its plugin runtime. Plugins are separate JAR files loaded from a `plugins/` directory. Each plugin runs in its own ClassLoader for isolation.

```
┌──────────────────────────────────────────────────────────────┐
│                      fiely-core                              │
│                  (Spring Boot Application)                   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐    │
│  │               Plugin Manager (PF4J)                  │    │
│  │                                                      │    │
│  │  ┌──────────┐  ┌──────────┐  ┌────────────────────┐ │    │
│  │  │ AI       │  │ Storage  │  │ Auth               │ │    │
│  │  │ Provider │  │ Provider │  │ Provider           │ │    │
│  │  └────┬─────┘  └────┬─────┘  └────────┬───────────┘ │    │
│  │       │              │                 │             │    │
│  │  ┌────┴─────┐  ┌────┴─────┐  ┌────────┴───────────┐ │    │
│  │  │ File     │  │ Notif.   │  │ Third-Party        │ │    │
│  │  │ Processor│  │ Provider │  │ Apps               │ │    │
│  │  └──────────┘  └──────────┘  └────────────────────┘ │    │
│  └──────────────────────────────────────────────────────┘    │
│                                                              │
│  Built-in defaults:                                          │
│  • JWT Auth     • Local Filesystem     • /api/ping           │
└──────────────────────────────────────────────────────────────┘
        │
        ▼
   plugins/
   ├── fiely-ai-ollama-1.0.0.jar
   ├── fiely-ai-openai-1.0.0.jar
   ├── fiely-auth-oidc-1.0.0.jar
   └── third-party-app-1.0.0.jar
```

---

## Module Structure

The plugin system lives in a **Gradle multi-project build** inside `fiely-backend/`. First-party plugins (auth, storage, AI, etc.) are developed in the same monorepo alongside the core — this ensures a single CI build, consistent versioning, and easy cross-module refactoring. Third-party plugins live in their own repositories.

```
fiely/
└── fiely-backend/
    ├── build.gradle.kts              # Root build (shared config, dependency versions)
    ├── settings.gradle.kts           # Declares all submodules
    │
    ├── fiely-plugin-api/             # Shared interfaces + DTOs (Kotlin, no Spring)
    ├── fiely-core/                   # Spring Boot app + Plugin Manager
    │
    └── plugins/                      # First-party plugins (monorepo)
        ├── fiely-auth-jwt/           # Built-in JWT / database auth
        ├── fiely-auth-oidc/          # OIDC / Keycloak
        ├── fiely-auth-ldap/          # LDAP / Active Directory
        ├── fiely-storage-local/      # Local filesystem storage
        ├── fiely-ai-ollama/          # Ollama (local AI)
        ├── fiely-ai-openai/          # OpenAI API
        ├── fiely-ai-claude/          # Anthropic Claude API
        ├── fiely-processor-text/     # Text extraction (PDF, DOCX)
        └── fiely-notify-email/       # E-Mail notifications
```

### Dependency Graph

```
                    fiely-plugin-api
                   (Kotlin interfaces)
                     ▲            ▲
                     │            │
              ┌──────┘            └──────────────┐
              │                                  │
         fiely-core                     plugins/* (first-party)
    (Spring Boot app)              (each a standalone JAR)
              │                                  │
              │         ┌────────────────────────┘
              ▼         ▼
        runtime classpath:
    core loads plugin JARs via PF4J
```

- **fiely-plugin-api** has zero Spring dependencies — only Kotlin stdlib, PF4J API, and shared DTOs.
- **fiely-core** depends on `fiely-plugin-api` (implementation) and Spring Boot.
- **Each plugin** depends on `fiely-plugin-api` (compileOnly) — the API is provided by the core at runtime.
- First-party and third-party plugins use the exact same API. There is no privileged internal API for first-party plugins.

### First-Party vs. Third-Party Plugins

| | First-Party | Third-Party |
|---|---|---|
| Location | `fiely-backend/plugins/` (monorepo) | Own repository |
| Dependency | `fiely-plugin-api` via Gradle project reference | `fiely-plugin-api` via Maven Central / GitHub Packages |
| Build | Built together with core in CI | Built independently |
| Distribution | Bundled with Fiely releases | Downloaded separately or via plugin marketplace |
| Trust level | Shipped by the Fiely team | Requires admin approval for permissions |

### fiely-plugin-api

A **minimal** Kotlin module that both `fiely-core` and all plugins depend on. Contains:

- Extension point interfaces (`AIProvider`, `StorageProvider`, etc.)
- Shared DTOs and value objects
- Event types
- No Spring, no heavy dependencies — keeps plugin JARs small

### fiely-core

The main Spring Boot application. Contains:

- `FielyPluginManager` — PF4J `SpringPluginManager` subclass
- Event bridge between Spring's `ApplicationEventPublisher` and plugin listeners
- Plugin configuration loading from `application.yml`
- REST API, database access, business logic
- No extension point implementations — those live in plugins

---

## Extension Points

### AIProvider

Pluggable AI backends for embedding, completion, and chat.

```kotlin
interface AIProvider : ExtensionPoint {
    val id: String
    val displayName: String

    fun embed(text: String): List<Float>
    fun complete(prompt: String): String
    fun chat(messages: List<Message>): Flow<String>

    fun isAvailable(): Boolean
}
```

**Built-in:** None — AI is opt-in. Users install the provider plugin they want.

**Planned plugins:** `fiely-ai-ollama`, `fiely-ai-openai`, `fiely-ai-claude`

---

### StorageProvider

Pluggable file storage backends.

```kotlin
interface StorageProvider : ExtensionPoint {
    val id: String

    fun store(path: StoragePath, data: InputStream, size: Long): FileReference
    fun retrieve(ref: FileReference): InputStream
    fun delete(ref: FileReference)
    fun exists(ref: FileReference): Boolean
    fun getSize(ref: FileReference): Long
}
```

**Built-in:** `LocalFilesystemStorageProvider` — stores files on the local filesystem. This is the default and requires zero configuration beyond a storage directory.

**Future plugins:** S3-compatible storage, WebDAV target, etc.

---

### AuthProvider

Pluggable authentication methods. Built-in JWT auth always exists as fallback.

```kotlin
interface AuthProvider : ExtensionPoint {
    val id: String
    val displayName: String

    fun supports(type: AuthType): Boolean
    fun authenticate(request: AuthRequest): AuthResult
    fun getUserInfo(token: String): UserInfo?
    fun refresh(refreshToken: String): AuthResult?
}

enum class AuthType {
    USERNAME_PASSWORD,
    OIDC,
    SAML,
    LDAP,
    API_KEY
}

sealed class AuthResult {
    data class Success(val token: TokenPair, val user: UserInfo) : AuthResult()
    data class Failure(val reason: String) : AuthResult()
    data class MfaRequired(val challengeId: String) : AuthResult()
}
```

**Built-in:** JWT-based username/password authentication (in `fiely-core`).

**Planned plugins:** `fiely-auth-oidc`, `fiely-auth-ldap`, `fiely-auth-saml`

---

### FileProcessor

Processes files after upload — thumbnail generation, text extraction, virus scanning, etc. Multiple processors can be active at the same time.

```kotlin
interface FileProcessor : ExtensionPoint {
    val id: String
    val supportedMimeTypes: Set<String>  // e.g. ["application/pdf", "image/*"]

    fun process(context: FileProcessingContext): FileProcessingResult
    fun priority(): Int = 0  // Lower runs first
}

data class FileProcessingContext(
    val fileRef: FileReference,
    val metadata: FileMetadata,
    val inputStream: InputStream
)

sealed class FileProcessingResult {
    data class Success(val outputs: Map<String, Any> = emptyMap()) : FileProcessingResult()
    data class Skip(val reason: String) : FileProcessingResult()
    data class Error(val message: String) : FileProcessingResult()
}
```

**Use cases:**
- Thumbnail generation for images and PDFs
- Text extraction for semantic search (PDF, DOCX, TXT)
- Virus scanning (ClamAV integration)
- EXIF data extraction from photos

---

### NotificationProvider

Pluggable notification delivery.

```kotlin
interface NotificationProvider : ExtensionPoint {
    val id: String
    val displayName: String

    fun send(notification: Notification): Boolean
    fun supports(channel: NotificationChannel): Boolean
}

enum class NotificationChannel {
    EMAIL,
    PUSH,
    WEBHOOK,
    IN_APP
}
```

**Built-in:** `InAppNotificationProvider` — stores notifications in the database, displayed in the UI.

**Planned plugins:** `fiely-notify-email`, `fiely-notify-push`, `fiely-notify-webhook`

---

### FielyApp (Third-Party Apps)

The most powerful extension point. Allows third-party developers to add features with their own REST endpoints, background jobs, and event listeners.

```kotlin
interface FielyApp : ExtensionPoint {
    val manifest: AppManifest

    fun onEnable(context: AppContext)
    fun onDisable()
}

data class AppManifest(
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String,
    val requiredPermissions: Set<AppPermission>
)

enum class AppPermission {
    READ_FILES,
    WRITE_FILES,
    READ_USERS,
    MANAGE_SHARES,
    SEND_NOTIFICATIONS
}
```

Third-party apps interact with Fiely exclusively through `AppContext` — a sandboxed API that controls what a plugin can and cannot do:

```kotlin
interface AppContext {
    // Configuration
    fun getConfig(): Map<String, String>

    // REST routes
    fun registerRoute(method: HttpMethod, path: String, handler: RouteHandler)

    // Events
    fun on(eventType: KClass<out FielyEvent>, listener: (FielyEvent) -> Unit)

    // Core services (read-only or permission-gated)
    fun currentUser(): UserInfo?
    fun fileService(): FileServiceApi      // Scoped to granted permissions
    fun notificationService(): NotificationServiceApi

    // No direct database access. No raw Spring context.
}
```

---

## Event System

Plugins can listen to lifecycle events emitted by the core. Events are published via Spring's `ApplicationEventPublisher` internally and bridged to plugin listeners.

### Core Events

```kotlin
sealed class FielyEvent(val timestamp: Instant = Instant.now())

// File events
data class FileUploadedEvent(val file: FileMetadata, val userId: String) : FielyEvent()
data class FileDeletedEvent(val fileId: String, val userId: String) : FielyEvent()
data class FileMovedEvent(val fileId: String, val from: String, val to: String) : FielyEvent()

// Share events
data class ShareCreatedEvent(val shareId: String, val fileId: String) : FielyEvent()
data class ShareAccessedEvent(val shareId: String, val accessedBy: String?) : FielyEvent()

// User events
data class UserCreatedEvent(val userId: String) : FielyEvent()
data class UserDeletedEvent(val userId: String) : FielyEvent()
data class UserLoginEvent(val userId: String, val authProvider: String) : FielyEvent()
```

### Plugin Event Listener

Plugins implement `PluginEventListener` to react to events:

```kotlin
interface PluginEventListener : ExtensionPoint {
    fun onEvent(event: FielyEvent)
}
```

Or use the typed convenience interface in `FielyApp` via `AppContext.on(...)`.

---

## Plugin Lifecycle

```
  install          resolve          start           stop
    │                │                │               │
    ▼                ▼                ▼               ▼
┌────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│CREATED │───▶│ RESOLVED │───▶│ STARTED  │───▶│ STOPPED  │
└────────┘    └──────────┘    └──────────┘    └──────────┘
                                                    │
                                               uninstall
                                                    │
                                                    ▼
                                              ┌──────────┐
                                              │ REMOVED  │
                                              └──────────┘
```

- **CREATED** — JAR found in `plugins/` directory
- **RESOLVED** — Dependencies checked, ClassLoader created
- **STARTED** — `onEnable()` called, extension points registered
- **STOPPED** — `onDisable()` called, extension points unregistered
- **REMOVED** — Plugin JAR deleted, ClassLoader garbage collected

Plugins are loaded on application startup. Hot-reloading (load/unload at runtime) is a future goal, not required for Phase 1.

---

## Plugin Configuration

Each plugin reads its configuration from `application.yml` under the `fiely.plugins` namespace:

```yaml
fiely:
  plugins:
    dir: ./plugins                  # Plugin directory (default: ./plugins)
    enabled:                         # Explicitly enable/disable plugins
      - fiely-ai-ollama
      - fiely-auth-oidc

    # Per-plugin configuration
    fiely-ai-ollama:
      base-url: http://localhost:11434
      model: llama3
      embedding-model: nomic-embed-text

    fiely-ai-openai:
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o

    fiely-auth-oidc:
      issuer-url: https://keycloak.example.com/realms/fiely
      client-id: fiely
      client-secret: ${OIDC_CLIENT_SECRET}
```

Plugins access their own config via `AppContext.getConfig()` or PF4J's built-in configuration mechanism.

---

## Plugin Development (Third-Party)

A third-party developer creates a plugin by:

1. Creating a Gradle/Maven project with `fiely-plugin-api` as a dependency
2. Implementing one or more extension point interfaces
3. Annotating extensions with `@Extension`
4. Packaging as a JAR with a `plugin.properties` descriptor

### Example: Simple File Processor Plugin

```kotlin
// build.gradle.kts
dependencies {
    compileOnly("cloud.fiely:fiely-plugin-api:1.0.0")
}

// plugin.properties
plugin.id=fiely-watermark
plugin.version=1.0.0
plugin.class=com.example.WatermarkPlugin
plugin.provider=Example Corp
plugin.description=Adds watermarks to uploaded images
```

```kotlin
class WatermarkPlugin : Plugin() // PF4J Plugin base class

@Extension
class WatermarkProcessor : FileProcessor {
    override val id = "watermark"
    override val supportedMimeTypes = setOf("image/png", "image/jpeg")

    override fun process(context: FileProcessingContext): FileProcessingResult {
        // Add watermark to image ...
        return FileProcessingResult.Success()
    }
}
```

### Example: Custom FielyApp

```kotlin
@Extension
class MyApp : FielyApp {
    override val manifest = AppManifest(
        id = "my-custom-app",
        name = "My Custom App",
        version = "1.0.0",
        author = "Developer",
        description = "A custom Fiely app",
        requiredPermissions = setOf(AppPermission.READ_FILES)
    )

    override fun onEnable(context: AppContext) {
        context.registerRoute(HttpMethod.GET, "/api/apps/my-app/status") { request ->
            RouteResponse(200, mapOf("status" to "running"))
        }

        context.on(FileUploadedEvent::class) { event ->
            // React to file uploads
        }
    }

    override fun onDisable() {
        // Cleanup
    }
}
```

---

## Security

### Plugin Isolation

- Each plugin runs in its **own ClassLoader** (PF4J default)
- Plugins cannot access other plugins' classes directly
- Plugins cannot access Spring's `ApplicationContext` — only `AppContext`

### Permission Model

Third-party apps must declare required permissions in their `AppManifest`. The admin must approve these permissions before the plugin can be enabled. This prevents plugins from silently accessing data they shouldn't.

### Constraints

- No direct database access — plugins use the service APIs provided through `AppContext`
- No filesystem access outside the plugin's own data directory
- Network access is not restricted by the plugin framework (left to container/network-level controls)
- Plugin JARs should be signed in a future version to verify integrity

---

## Default Plugins (First-Party)

The following first-party plugins ship with every Fiely release and are enabled by default. They live in the monorepo under `fiely-backend/plugins/` and are built alongside the core.

| Plugin | Extension Point | Notes |
|---|---|---|
| `fiely-auth-jwt` | `AuthProvider` | JWT / database auth, default, no external dependency |
| `fiely-storage-local` | `StorageProvider` | Local filesystem storage, zero config |
| `fiely-processor-text` | `FileProcessor` | Text extraction from PDF, DOCX, TXT for search |

These defaults ensure Fiely works out of the box. All other first-party plugins are opt-in:

| Plugin | Extension Point | Notes |
|---|---|---|
| `fiely-auth-oidc` | `AuthProvider` | OIDC / Keycloak integration |
| `fiely-auth-ldap` | `AuthProvider` | LDAP / Active Directory sync |
| `fiely-ai-ollama` | `AIProvider` | Local AI via Ollama |
| `fiely-ai-openai` | `AIProvider` | OpenAI API (GPT-4o) |
| `fiely-ai-claude` | `AIProvider` | Anthropic Claude API |
| `fiely-notify-email` | `NotificationProvider` | E-Mail notifications via SMTP |

Users enable opt-in plugins by adding them to `fiely.plugins.enabled` in `application.yml` and providing the required configuration.

---

## Roadmap

### Phase 1 — Foundation
- [ ] Convert `fiely-backend` to Gradle multi-project build (Kotlin)
- [ ] Create `fiely-plugin-api` submodule with Kotlin interfaces
- [ ] Integrate PF4J + pf4j-spring into `fiely-core`
- [ ] Implement `StorageProvider` extension point
- [ ] Build `fiely-storage-local` plugin (local filesystem)
- [ ] Implement `AuthProvider` extension point
- [ ] Build `fiely-auth-jwt` plugin (JWT / database auth)
- [ ] Plugin configuration via `application.yml`
- [ ] Plugin lifecycle (load on startup, enable/disable via config)

### Phase 2 — AI & Processing
- [ ] Implement `AIProvider` extension point
- [ ] Build `fiely-ai-ollama` plugin
- [ ] Build `fiely-ai-openai` plugin
- [ ] Build `fiely-ai-claude` plugin
- [ ] Implement `FileProcessor` extension point
- [ ] Build `fiely-processor-text` plugin (PDF, DOCX extraction)
- [ ] Event system bridge (Spring Events → Plugin listeners)

### Phase 3 — Auth & Notifications
- [ ] Build `fiely-auth-oidc` plugin (Keycloak / OIDC)
- [ ] Build `fiely-auth-ldap` plugin (LDAP / AD)
- [ ] Implement `NotificationProvider` extension point
- [ ] Build `fiely-notify-email` plugin (SMTP)

### Phase 4 — Third-Party Apps
- [ ] Implement `FielyApp` extension point with `AppContext`
- [ ] Frontend plugin mechanism (webapp/ assets, manifest.json)
  - [ ] Publish `@fiely/host` v1 (host API) and `@fiely/ui` (component library)
  - [ ] Import-map-based loader with React/host externalized
  - [ ] In-process plugin mount point with `.fiely-plugin-root` CSS scoping
  - [ ] Aggregate manifest endpoint (`GET /api/plugins/manifests`)
  - [ ] Content-hashed, long-cacheable bundle serving (`/apps/{id}/{version}/...`)
  - [ ] Strict CSP: `script-src` pinned to core origin, `connect-src` pinned to Fiely API
  - [ ] `hostApiVersion` compatibility gating + admin "incompatible" UI
  - [ ] Admin install flow with explicit permission approval
  - [ ] `create-fiely-plugin` scaffold + `@fiely/plugin-dev` Vite plugin with HMR
- [ ] Permission model and admin approval flow (backend + frontend enforcement)
- [ ] Plugin hot-reloading (load/unload at runtime)
- [ ] Publish `fiely-plugin-api` to Maven Central / GitHub Packages
- [ ] Plugin marketplace / registry (future)

---

## Decisions

- **Kotlin:** The entire backend will be written in Kotlin. This applies to `fiely-plugin-api`, `fiely-core`, and all first-party plugins. Spring Boot has excellent Kotlin support (null safety, coroutines, DSLs). The existing Java sources will be migrated to Kotlin.
- **Monorepo:** First-party plugins live in the same repository under `fiely-backend/plugins/`. This means a single CI build, consistent versioning, and easy refactoring across core and plugins. Third-party plugins use their own repositories and depend on `fiely-plugin-api` via Maven Central / GitHub Packages.
- **Multi-module Gradle:** `fiely-backend` becomes a Gradle multi-project build with `fiely-plugin-api`, `fiely-core`, and each plugin as a submodule. Shared configuration (Kotlin version, dependency versions) is managed in the root `build.gradle.kts`.
- **Plugin UI:** Third-party apps can contribute frontend components. Each plugin JAR may include a `webapp/` directory with static assets (JS bundles, CSS). The core serves these under `/apps/{plugin-id}/{version}/` and the React frontend discovers them via an aggregate manifest API (`GET /api/plugins/manifests`). Plugin bundles are loaded as native ESM via browser import maps that pin `react`, `react-dom`, and `@fiely/host` to the core's versions — no "two Reacts" problem, no Module Federation runtime. **All plugins run in-process**: the plugin's exported component is mounted directly in the host React tree with a `FielyHost` instance passed as a prop. Isolation comes from admin review before install, backend enforcement of the declared `AppPermission` set on every API call, and CSS scoping inside a per-plugin mount wrapper — not from a runtime sandbox. See the full contract in [Frontend Plugin Mechanism](#frontend-plugin-mechanism) below.

### Frontend Plugin Mechanism

Goals for the frontend plugin system:

- **Zero build-time coupling** — adding a plugin never rebuilds the core frontend.
- **Versioned contract** — plugins compile against a stable host API; incompatible plugins are rejected up-front, not at runtime.
- **Unified in-process model** — every plugin runs directly in the host React tree. Trust is established at install time and enforced at the backend API layer, not by a runtime sandbox. This mirrors VS Code extensions, IntelliJ plugins, and Rails engines, and gives every plugin shared state (router, theme, query cache) plus a trivial DX.
- **Good DX** — a plugin author can scaffold, run with HMR, and ship without knowing anything about the core's build system.

#### Bundle layout

A plugin JAR may contain an optional `webapp/` directory:

```
plugin-jar/
├── plugin.properties
├── classes/                          # Backend code (Kotlin)
└── webapp/                           # Frontend assets (optional)
    ├── manifest.json                 # Declares UI entry points
    ├── assets/
    │   ├── index.[hash].js           # ESM bundle, React/host externalized
    │   ├── PdfAnalyzer.[hash].js     # Optional additional entry bundles
    │   └── style.[hash].css          # Optional, scoped under .fiely-plugin-root
    └── icons/                        # Optional SVG icons referenced from manifest
```

The backend serves `webapp/` under a content-hashed, long-cacheable URL:

```
GET /apps/{plugin-id}/{version}/manifest.json
GET /apps/{plugin-id}/{version}/assets/{file}
```

`{version}` is taken from `plugin.properties` so a plugin update invalidates caches automatically. Bundles are served with `Cache-Control: public, max-age=31536000, immutable`; the manifest is served with `no-cache` so new versions are picked up on next load.

#### Manifest schema (`webapp/manifest.json`)

```json
{
  "id": "my-app",
  "name": "My App",
  "version": "1.2.0",
  "hostApiVersion": "1",
  "trust": "third-party",
  "icon": "icons/app.svg",
  "entryPoints": [
    {
      "type": "page",
      "id": "dashboard",
      "path": "/apps/my-app",
      "label": "My App",
      "icon": "icons/app.svg",
      "bundle": "assets/index.abcd1234.js",
      "export": "default",
      "navLocation": "sidebar"
    },
    {
      "type": "file-action",
      "id": "analyze-pdf",
      "mimeTypes": ["application/pdf"],
      "label": "Analyze PDF",
      "icon": "icons/analyze.svg",
      "bundle": "assets/PdfAnalyzer.abcd1234.js",
      "export": "PdfAnalyzer"
    },
    {
      "type": "settings-panel",
      "id": "settings",
      "label": "My App Settings",
      "scope": "admin",
      "bundle": "assets/index.abcd1234.js",
      "export": "Settings"
    }
  ]
}
```

Required top-level fields: `id`, `version`, `hostApiVersion`, `entryPoints`. `hostApiVersion` must match a version the running core advertises; otherwise the plugin is not loaded and surfaces as "incompatible" in the admin UI.

#### Loading model — import maps + native ESM

The core emits an **import map** at page load that pins shared singletons:

```html
<script type="importmap">
{
  "imports": {
    "react":        "/vendor/react@18.3.1/index.js",
    "react-dom":    "/vendor/react-dom@18.3.1/index.js",
    "@fiely/host":  "/vendor/fiely-host@1/index.js"
  }
}
</script>
```

Plugin bundles are built as **ESM with these packages externalized**. The browser resolves the imports to the singletons served by the core — eliminating the "two Reacts" problem and keeping plugin bundles small (a typical plugin ships in ~5–20 KB gzipped).

Plugins are loaded via native dynamic `import()`:

```ts
const module = await import(`/apps/${id}/${version}/${entryPoint.bundle}`);
const Component = module[entryPoint.export ?? 'default'];
```

No Webpack, no Module Federation, no Vite runtime required on the plugin side. Plugin authors ship a standard ESM bundle.

> **Rejected alternative — Module Federation.** Considered and rejected for Phase 4: it requires plugin authors to adopt Webpack/Rspack config, fights with Vite's native ESM, and duplicates what browser-native import maps already solve cleanly.

#### Provenance tiers

| Tier | Who | React version | How it's established |
|---|---|---|---|
| `core` | Shipped in the core bundle | pinned | compiled in |
| `first-party` | Plugins under `fiely-backend/plugins/` | import map singleton | built + signed by the Fiely team |
| `third-party` | Community / marketplace plugins | import map singleton | admin review + explicit permission approval |

All three tiers run **in-process**. The `trust` field in the manifest is a *provenance* label — it drives the install banner, the badge on nav entries, and which permissions may be auto-granted. It is set by the core based on where the JAR came from, never by the plugin itself.

Isolation does not come from a runtime sandbox; it comes from:

- **Admin review.** Third-party JARs go through a review + explicit permission-approval step before they can be enabled.
- **Backend permission enforcement.** Every `host.files.*` call ultimately hits a Fiely API endpoint that checks the plugin's `AppPermission` set. A `WRITE_FILES`-less plugin that calls `host.files.upload` gets a `PermissionError` from the backend — the frontend cannot bypass it.
- **Bundle provenance.** The core's import map is the only way plugin code gets `react`, `react-dom`, and `@fiely/host`. Bundles are loaded by URL from `/apps/{id}/{version}/...`, which the backend serves; plugins cannot `import()` arbitrary remote modules because CSP blocks it.
- **CSS scoping.** Plugins must render inside the host's `.fiely-plugin-root` wrapper and must not attach global stylesheets. The loader rejects bundles that inject `<style>` tags at the document level.

This approach mirrors how VS Code extensions, IntelliJ plugins, and Rails engines operate: trust is established at install time and enforced at the API layer, not by a per-plugin process boundary.

#### Host API (`@fiely/host`)

Plugins import from `@fiely/host`, which is pinned by the core's import map so every plugin shares the exact same types and helpers. The package ships as types + two small loader helpers; there is no RPC layer.

```ts
// @fiely/host — v1
export interface FielyHost {
  // Identity
  readonly user: UserInfo;
  readonly theme: ThemeTokens;        // tokens matching fiely-frontend tailwind.config
  readonly hostApiVersion: '1';

  // Data access (already authenticated, respects backend permissions)
  fetch(input: RequestInfo, init?: RequestInit): Promise<Response>;

  // Files — typed wrappers around the file service
  files: {
    get(id: string): Promise<FileMetadata>;
    download(id: string): Promise<Blob>;
    list(folderId: string, opts?: ListOptions): Promise<FileMetadata[]>;
    // write operations only resolve if the plugin has WRITE_FILES permission
    upload(folderId: string, file: Blob, name: string): Promise<FileMetadata>;
  };

  // UI affordances
  ui: {
    toast(msg: string, kind?: 'info' | 'success' | 'warning' | 'error'): void;
    confirm(opts: ConfirmOptions): Promise<boolean>;
    navigate(path: string): void;
    openFile(id: string): void;
  };

  // Events — subset of backend events, scoped to the plugin's permissions
  on<E extends HostEvent>(type: E['type'], handler: (e: E) => void): () => void;

  // Configuration — the plugin's own config from application.yml
  config: Readonly<Record<string, string>>;

  // i18n
  t(key: string, params?: Record<string, string>): string;
}

export type HostEvent =
  | { type: 'file.uploaded'; file: FileMetadata }
  | { type: 'file.deleted'; fileId: string }
  | { type: 'navigation'; path: string };
```

Rules:

- Versioned. Breaking changes bump `hostApiVersion`. The core may support multiple versions in parallel during a deprecation window.
- Minimal by design. Anything a plugin needs that isn't here should be requested via an API proposal, not by reaching into globals.
- `window.Fiely` does **not** exist. Plugins must import from `@fiely/host`.

#### Entry point types

Each entry point declares the shape of the component it mounts. All props include `host: FielyHost`.

```ts
// type: "page"
type PageProps = {
  host: FielyHost;
  params: Record<string, string>;          // route params under the plugin's base path
};

// type: "file-action"
type FileActionProps = {
  host: FielyHost;
  file: FileMetadata;
  onClose(): void;
  onReplaced?(newFile: FileMetadata): void; // optional result channel
};

// type: "settings-panel"
type SettingsPanelProps = {
  host: FielyHost;
  scope: 'user' | 'admin';
};

// type: "sidebar-widget" (future)
type SidebarWidgetProps = { host: FielyHost };
```

Entry points are statically declared in the manifest so the core can render a placeholder (icon, label, skeleton) before the bundle has loaded.

#### Routing

The core owns React Router. A `page` entry point is mounted under its declared `path` with a `<Outlet>` slot, so a plugin can bring its own nested `<Routes>` if it needs sub-pages:

```tsx
// Core mounts approximately:
<Route path={entry.path + '/*'} element={<PluginHost entry={entry} />} />
```

Plugins must keep their routes under the declared base path. Cross-plugin navigation goes through `host.ui.navigate(path)` so the core can intercept (analytics, guards, permission checks).

#### Styling & the design system

- In-process plugins **must** use the shared `@fiely/ui` component library (buttons, inputs, modals, tables) so they inherit Tailwind tokens, dark mode, and accessibility defaults. Shipping raw `<style>` tags or a global CSS reset is forbidden and rejected at manifest-load time by a CSS-scoping check.
- Iframe plugins can ship whatever CSS they want — they're isolated — but `@fiely/ui` is still offered so they look native by default.
- Theme tokens (colors, radii, spacing) are exposed via `host.theme` so plugins that do write custom CSS can match the host.

`@fiely/ui` is versioned alongside `@fiely/host` and published to npm.

#### Manifest aggregation endpoint

The core exposes both a per-plugin and an aggregate endpoint:

```
GET /api/plugins/manifests           → PluginManifest[]   (one round-trip at boot)
GET /api/plugins/{id}/manifest       → PluginManifest     (for admin pages / deep links)
```

The aggregate response is ETagged so the frontend can revalidate cheaply on subsequent loads.

#### Versioning & compatibility

- `hostApiVersion` in the manifest is a **major version string** (`"1"`, `"2"`). Minor additions don't bump it.
- The core advertises the versions it supports via `GET /api/plugins/host-api/versions`.
- Incompatible plugins are still visible in the admin UI as "requires host API v2" — they just don't mount.
- Plugins may declare a minimum Fiely version (`"minFielyVersion": "0.5.0"`) for finer-grained gating.

#### Security model (frontend)

Every plugin runs in the same document as the core. Security is layered, not sandbox-based:

- **Source of truth: the backend.** `host.files.*`, `host.fetch`, and anything else that touches data calls Fiely API endpoints that are guarded by the plugin's `AppPermission` set. A plugin can call `host.files.upload` all it wants — the backend still rejects it without `WRITE_FILES`, and the host surfaces the rejection as a typed `PermissionError`.
- **Admin review before install.** Third-party JARs are inspected and their declared permissions approved explicitly. An install banner makes the permission set obvious ("This app wants to read all your files").
- **Controlled code loading.** Bundles are only loadable from `/apps/{id}/{version}/...` on the core origin. Page-level CSP pins `script-src` to the core origin and `connect-src` to the Fiely API — a plugin cannot fetch or `import()` arbitrary third-party code at runtime.
- **Shared-singleton enforcement.** The core's import map is the only resolver for `react`, `react-dom`, and `@fiely/host`. Bundles that try to ship their own copies fail to load.
- **CSS scoping.** Plugins mount inside `.fiely-plugin-root`; the loader rejects bundles that attach stylesheets outside it. Plugins are strongly encouraged to use `@fiely/ui`.
- **No eval of plugin-supplied strings.** The host never evaluates plugin-provided templates or HTML strings; React reconciliation is the only path from plugin code to the DOM.

This is a weaker boundary than an iframe sandbox and it is a deliberate trade-off: we get shared state (router, query cache, theme context), design-system consistency, and a trivial DX, at the cost of treating the plugin install step as the real trust boundary. For workloads that truly require code-level isolation, Fiely's answer is "keep that code on the backend and call it over `host.fetch`".

#### Developer experience

A plugin author workflow that Just Works is non-negotiable:

```bash
npx create-fiely-plugin my-app
cd my-app
npm run dev                    # HMR against a running Fiely instance
npm run build                  # produces webapp/ with hashed bundles
./gradlew :plugins:my-app:jar  # bundles backend + webapp/ into a JAR
```

- `create-fiely-plugin` scaffolds a Vite project with `@fiely/host` and `@fiely/ui` preinstalled, externals configured, and a working `file-action` example.
- `@fiely/plugin-dev` provides a Vite plugin that:
  - injects the host API import map in dev,
  - proxies `/api` to a developer's local Fiely backend,
  - hot-reloads the plugin bundle in the running UI.
- Published templates for each entry-point type (page, file-action, settings-panel).

#### Open questions

Items intentionally deferred past Phase 4:

- **Cross-plugin communication.** If Plugin A wants to extend Plugin B's page — do we allow it? Probably yes via a pub/sub channel, but out of scope for v1.
- **Shared query cache.** Can plugins share a TanStack Query cache with the host, or should we expose a plugin-scoped cache to avoid cross-plugin invalidation races? Leaning: expose it via `@fiely/host` rather than letting plugins reach for `useQueryClient`.
- **Offline / Service Worker.** Plugin assets are cacheable; whether plugins can register their own service workers is undecided.
- **Plugin marketplace UX.** Discovery, signing, reviews, and a trust-chain model are a separate design doc.
