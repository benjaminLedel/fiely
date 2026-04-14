package cloud.fiely.plugin

import org.pf4j.ExtensionPoint
import kotlin.reflect.KClass

/**
 * Extension point for third-party applications.
 *
 * Apps can register REST routes, listen to events, and (optionally) ship
 * frontend components via a webapp/ directory in the plugin JAR.
 */
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
    val requiredPermissions: Set<AppPermission> = emptySet()
)

enum class AppPermission {
    READ_FILES,
    WRITE_FILES,
    READ_USERS,
    MANAGE_SHARES,
    SEND_NOTIFICATIONS
}

/**
 * Sandboxed API provided to third-party apps. No direct database or
 * Spring context access — only well-defined service APIs.
 */
interface AppContext {
    fun getConfig(): Map<String, String>
    fun registerRoute(method: HttpMethod, path: String, handler: RouteHandler)
    fun <T : FielyEvent> on(eventType: KClass<T>, listener: (T) -> Unit)
    fun currentUser(): UserInfo?
}

enum class HttpMethod { GET, POST, PUT, PATCH, DELETE }

fun interface RouteHandler {
    fun handle(request: RouteRequest): RouteResponse
}

data class RouteRequest(
    val pathParams: Map<String, String> = emptyMap(),
    val queryParams: Map<String, String> = emptyMap(),
    val body: String? = null,
    val headers: Map<String, String> = emptyMap()
)

data class RouteResponse(
    val status: Int,
    val body: Any? = null,
    val headers: Map<String, String> = emptyMap()
)
