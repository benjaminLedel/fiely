package cloud.fiely.auth.web

import cloud.fiely.plugin.AuthProvider
import cloud.fiely.plugin.UserInfo
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.ObjectProvider
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Extracts the authenticated principal from the inbound request.
 *
 * The flow mirrors `AuthController.me` — pull the `Authorization: Bearer …`
 * token, iterate every registered [AuthProvider] until one recognises it — but
 * also resolves the user's tenant_id from `auth_users`. Controllers that need
 * "who is calling?" should depend on this component rather than re-implementing
 * the token parsing.
 *
 * Fiely does not use Spring Security, so there is no `SecurityContext` or
 * filter-based equivalent: callers pull the principal at the top of each
 * handler and respond with 401 on null.
 */
@Component
class CurrentUserResolver(
    private val authProviders: ObjectProvider<AuthProvider>,
    private val jdbc: JdbcTemplate,
) {
    /** Returns the authenticated user, or `null` if no valid token is present. */
    fun resolve(request: HttpServletRequest): AuthenticatedUser? {
        val header = request.getHeader("Authorization") ?: return null
        if (!header.startsWith(BEARER_PREFIX, ignoreCase = true)) return null
        val token = header.substring(BEARER_PREFIX.length).trim()
        if (token.isEmpty()) return null

        val user = authProviders.stream().toList()
            .firstNotNullOfOrNull { it.getUserInfo(token) }
            ?: return null

        val userId = runCatching { UUID.fromString(user.id) }.getOrNull() ?: return null
        val tenantId = findTenantId(userId) ?: return null

        return AuthenticatedUser(info = user, userId = userId, tenantId = tenantId)
    }

    private fun findTenantId(userId: UUID): UUID? {
        val results = jdbc.queryForList(
            "SELECT tenant_id FROM auth_users WHERE id = ?",
            userId,
        )
        val raw = results.firstOrNull()?.get("tenant_id") ?: return null
        return when (raw) {
            is UUID -> raw
            is String -> runCatching { UUID.fromString(raw) }.getOrNull()
            else -> null
        }
    }

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}

/**
 * The resolved principal for a request. [info] comes from the active
 * [AuthProvider]; [tenantId] is resolved from `auth_users`.
 */
data class AuthenticatedUser(
    val info: UserInfo,
    val userId: UUID,
    val tenantId: UUID,
)
