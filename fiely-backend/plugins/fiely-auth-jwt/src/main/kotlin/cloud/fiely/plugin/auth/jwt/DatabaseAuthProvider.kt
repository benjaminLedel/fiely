package cloud.fiely.plugin.auth.jwt

import cloud.fiely.plugin.AuthProvider
import cloud.fiely.plugin.AuthRequest
import cloud.fiely.plugin.AuthResult
import cloud.fiely.plugin.AuthType
import cloud.fiely.plugin.PluginServices
import cloud.fiely.plugin.TokenPair
import cloud.fiely.plugin.UserInfo
import org.pf4j.Extension
import org.slf4j.LoggerFactory

/**
 * Default `AuthProvider` implementation — authenticates username/password
 * against the `auth_users` table (shipped via [AuthJwtMigrations]) and issues
 * short-lived HS256 JWT access + refresh tokens.
 *
 * Configuration (read from [PluginServices.configFor]):
 *  - `secret`            — HMAC secret (required, must be at least 32 chars)
 *  - `issuer`            — `iss` claim, default `fiely`
 *  - `access-token-ttl`  — seconds, default 3600 (1 hour)
 *  - `refresh-token-ttl` — seconds, default 604800 (7 days)
 *
 * The refresh token is a JWT with a `typ=refresh` claim — simple, stateless,
 * and good enough as a starting point. A production deployment will want
 * refresh-token rotation and server-side revocation, which can be added in
 * a later iteration.
 */
@Extension
class DatabaseAuthProvider : AuthProvider {

    private val log = LoggerFactory.getLogger(DatabaseAuthProvider::class.java)

    override val id: String = "fiely-auth-jwt"
    override val displayName: String = "Database (username / password)"

    private val config by lazy { PluginServices.configFor("fiely-auth-jwt") }
    private val users by lazy { UserRepository(PluginServices.dataSource) }
    private val hasher by lazy {
        val iters = (config["pbkdf2-iterations"] as? String)?.toIntOrNull()
        if (iters != null) PasswordHasher(iters) else PasswordHasher()
    }
    private val tokenService by lazy {
        val secret = (config["secret"] as? String).orEmpty().ifEmpty {
            // Dev fallback — logs a loud warning. Do NOT use in production.
            log.warn("fiely.plugins.fiely-auth-jwt.secret is not set — using an insecure dev-only secret")
            DEV_FALLBACK_SECRET
        }
        val issuer = (config["issuer"] as? String) ?: "fiely"
        JwtTokenService(secret, issuer)
    }
    private val accessTtl by lazy {
        (config["access-token-ttl"] as? String)?.toLongOrNull() ?: 3_600L
    }
    private val refreshTtl by lazy {
        (config["refresh-token-ttl"] as? String)?.toLongOrNull() ?: 604_800L
    }

    override fun supports(type: AuthType): Boolean = type == AuthType.USERNAME_PASSWORD

    override fun authenticate(request: AuthRequest): AuthResult {
        if (request.type != AuthType.USERNAME_PASSWORD) {
            return AuthResult.Failure("Unsupported auth type: ${request.type}")
        }
        val username = request.credentials["username"]?.trim().orEmpty()
        val password = request.credentials["password"].orEmpty()
        if (username.isEmpty() || password.isEmpty()) {
            return AuthResult.Failure("Missing username or password")
        }

        val user = users.findByUsername(username)
            ?: return AuthResult.Failure("Invalid credentials")
        if (!user.enabled) {
            return AuthResult.Failure("Account disabled")
        }

        val passwordChars = password.toCharArray()
        val verified = try {
            hasher.verify(passwordChars, user.passwordHash)
        } finally {
            passwordChars.fill('\u0000')
        }
        if (!verified) {
            return AuthResult.Failure("Invalid credentials")
        }

        return AuthResult.Success(tokens(user), user.toUserInfo())
    }

    override fun getUserInfo(token: String): UserInfo? {
        val claims = tokenService.verify(token) ?: return null
        if (claims["typ"] == "refresh") return null
        val subject = claims["sub"] as? String ?: return null
        return users.findById(subject)?.toUserInfo()
    }

    override fun refresh(refreshToken: String): AuthResult? {
        val claims = tokenService.verify(refreshToken) ?: return null
        if (claims["typ"] != "refresh") return null
        val subject = claims["sub"] as? String ?: return null
        val user = users.findById(subject) ?: return null
        if (!user.enabled) return AuthResult.Failure("Account disabled")
        return AuthResult.Success(tokens(user), user.toUserInfo())
    }

    private fun tokens(user: AuthUserRow): TokenPair {
        val baseClaims = mapOf(
            "username" to user.username,
            "email" to user.email,
        )
        val access = tokenService.issue(
            subject = user.id,
            claims = baseClaims + ("roles" to user.roles.joinToString(",")),
            ttlSeconds = accessTtl,
        )
        val refresh = tokenService.issue(
            subject = user.id,
            claims = mapOf("typ" to "refresh"),
            ttlSeconds = refreshTtl,
        )
        return TokenPair(
            accessToken = access,
            refreshToken = refresh,
            expiresInSeconds = accessTtl,
        )
    }

    private fun AuthUserRow.toUserInfo(): UserInfo = UserInfo(
        id = id,
        username = username,
        email = email,
        displayName = displayName,
        roles = roles,
    )

    companion object {
        private const val DEV_FALLBACK_SECRET =
            "dev-only-insecure-secret-change-me-change-me-change-me"
    }
}
