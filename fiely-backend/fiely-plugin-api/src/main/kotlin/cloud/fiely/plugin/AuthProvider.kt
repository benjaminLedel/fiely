package cloud.fiely.plugin

import org.pf4j.ExtensionPoint

/**
 * Extension point for pluggable authentication methods.
 *
 * The default implementation (fiely-auth-jwt) provides JWT-based database auth.
 * Additional providers (OIDC, LDAP, SAML) ship as separate plugins.
 */
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

data class AuthRequest(
    val type: AuthType,
    val credentials: Map<String, String>
)

sealed class AuthResult {
    data class Success(val token: TokenPair, val user: UserInfo) : AuthResult()
    data class Failure(val reason: String) : AuthResult()
    data class MfaRequired(val challengeId: String) : AuthResult()
}

data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long
)

data class UserInfo(
    val id: String,
    val username: String,
    val email: String?,
    val displayName: String?,
    val roles: Set<String> = emptySet()
)
