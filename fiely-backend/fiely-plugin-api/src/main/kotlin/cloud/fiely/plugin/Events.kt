package cloud.fiely.plugin

import java.time.Instant

/**
 * Base class for all Fiely lifecycle events.
 * Plugins can listen to these via [FielyApp.AppContext.on] or [PluginEventListener].
 */
sealed class FielyEvent(val timestamp: Instant = Instant.now())

// --- File events ---

data class FileUploadedEvent(val file: FileMetadata, val userId: String) : FielyEvent()
data class FileDeletedEvent(val fileId: String, val userId: String) : FielyEvent()
data class FileMovedEvent(val fileId: String, val from: String, val to: String) : FielyEvent()

// --- Share events ---

data class ShareCreatedEvent(val shareId: String, val fileId: String, val userId: String) : FielyEvent()
data class ShareAccessedEvent(val shareId: String, val accessedBy: String?) : FielyEvent()

// --- User events ---

data class UserCreatedEvent(val userId: String) : FielyEvent()
data class UserDeletedEvent(val userId: String) : FielyEvent()
data class UserLoginEvent(val userId: String, val authProvider: String) : FielyEvent()
