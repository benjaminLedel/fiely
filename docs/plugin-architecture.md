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

The plugin system is split into two Gradle submodules:

```
fiely/
└── fiely-backend/
    ├── fiely-plugin-api/       # Shared interfaces + DTOs (no Spring dependency)
    └── fiely-core/             # Spring Boot app + Plugin Manager + built-in defaults
```

### fiely-plugin-api

A **minimal** Kotlin module that both `fiely-core` and all plugins depend on. Contains:

- Extension point interfaces (`AIProvider`, `StorageProvider`, etc.)
- Shared DTOs and value objects
- Event types
- No Spring, no heavy dependencies — keeps plugin JARs small

### fiely-core

The main Spring Boot application. Contains:

- `FielyPluginManager` — PF4J `SpringPluginManager` subclass
- Built-in default implementations (JWT auth, local filesystem storage)
- Event bridge between Spring's `ApplicationEventPublisher` and plugin listeners
- Plugin configuration loading from `application.yml`

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

## Built-in Defaults

The following implementations live in `fiely-core` (not as plugins) and are always available:

| Component | Implementation | Notes |
|---|---|---|
| Auth | `JwtAuthProvider` | Default, no external dependency |
| Storage | `LocalFilesystemStorageProvider` | Stores files on disk |
| Notifications | `InAppNotificationProvider` | Database-backed, shown in UI |

These built-in defaults ensure Fiely works out of the box without any plugins installed. Plugins extend functionality but are never required for the core experience.

---

## Roadmap

### Phase 1 — Foundation
- [ ] Set up `fiely-plugin-api` Gradle submodule with Kotlin interfaces
- [ ] Integrate PF4J + pf4j-spring into `fiely-core`
- [ ] Implement `StorageProvider` extension point + `LocalFilesystemStorageProvider`
- [ ] Implement `AuthProvider` extension point + `JwtAuthProvider`
- [ ] Plugin configuration via `application.yml`
- [ ] Plugin lifecycle (load on startup, enable/disable via config)

### Phase 2 — AI & Processing
- [ ] Implement `AIProvider` extension point
- [ ] Build `fiely-ai-ollama` plugin
- [ ] Implement `FileProcessor` extension point
- [ ] Build text extraction processor (PDF, DOCX)
- [ ] Event system bridge (Spring Events → Plugin listeners)

### Phase 3 — Third-Party Apps
- [ ] Implement `FielyApp` extension point with `AppContext`
- [ ] Permission model and admin approval flow
- [ ] `NotificationProvider` extension point
- [ ] Plugin hot-reloading (load/unload at runtime)
- [ ] Plugin marketplace / registry (future)

---

## Decisions

- **Kotlin:** The entire backend will be written in Kotlin. This applies to `fiely-plugin-api`, `fiely-core`, and all first-party plugins. Spring Boot has excellent Kotlin support (null safety, coroutines, DSLs). The existing Java sources will be migrated to Kotlin.
- **Multi-module Gradle:** The introduction of `fiely-plugin-api` and `fiely-core` as submodules requires converting the current single-module `fiely-backend` into a Gradle multi-project build.
- **Plugin UI:** Third-party apps can contribute frontend components. Each plugin JAR may include a `webapp/` directory with static assets (JS bundles, CSS). The core serves these under `/apps/{plugin-id}/` and the React frontend discovers them via a plugin manifest API (`GET /api/plugins/{id}/manifest`). This enables plugins to add pages, settings panels, or file-action buttons in the UI.

### Frontend Plugin Mechanism

```
plugin-jar/
├── plugin.properties
├── classes/                  # Backend code (Kotlin)
└── webapp/                   # Frontend assets (optional)
    ├── manifest.json         # Declares UI entry points
    ├── index.js              # Bundled React component(s)
    └── style.css
```

```json
// webapp/manifest.json
{
  "id": "my-app",
  "entryPoints": [
    {
      "type": "page",
      "path": "/apps/my-app",
      "label": "My App",
      "icon": "puzzle",
      "bundle": "index.js"
    },
    {
      "type": "file-action",
      "mimeTypes": ["application/pdf"],
      "label": "Analyze PDF",
      "bundle": "index.js",
      "component": "PdfAnalyzer"
    }
  ]
}
```

The React frontend loads plugin UI components dynamically via [Module Federation](https://webpack.js.org/concepts/module-federation/) or dynamic `import()`. Plugin bundles are served as static assets by the backend — no build-time coupling between core frontend and plugin frontend.
