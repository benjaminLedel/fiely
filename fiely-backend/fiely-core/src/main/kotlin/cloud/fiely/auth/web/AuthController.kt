package cloud.fiely.auth.web

import cloud.fiely.plugin.AuthProvider
import cloud.fiely.plugin.AuthRequest
import cloud.fiely.plugin.AuthResult
import cloud.fiely.plugin.AuthType
import cloud.fiely.plugin.TokenPair
import cloud.fiely.plugin.UserInfo
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST endpoints wrapping the [AuthProvider] extension point.
 *
 * The controller picks the first provider that supports the requested
 * [AuthType] and delegates to it. In a single-tenant install that's the
 * built-in `fiely-auth-jwt` plugin; multi-provider setups (e.g. DB +
 * OIDC) will be addressed in a later iteration with explicit selection.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(private val authProviders: List<AuthProvider>) {

    private val log = LoggerFactory.getLogger(AuthController::class.java)

    @PostMapping("/login")
    fun login(@RequestBody body: LoginRequest): ResponseEntity<Any> {
        val provider = providerFor(AuthType.USERNAME_PASSWORD)
            ?: return serviceUnavailable("No auth provider available")

        val request = AuthRequest(
            type = AuthType.USERNAME_PASSWORD,
            credentials = mapOf(
                "username" to body.username,
                "password" to body.password,
            ),
        )
        return handle(provider.authenticate(request))
    }

    @PostMapping("/refresh")
    fun refresh(@RequestBody body: RefreshRequest): ResponseEntity<Any> {
        val provider = providerFor(AuthType.USERNAME_PASSWORD)
            ?: return serviceUnavailable("No auth provider available")

        val result = provider.refresh(body.refreshToken)
            ?: return ResponseEntity.status(401).body(ErrorResponse("Invalid refresh token"))
        return handle(result)
    }

    @GetMapping("/me")
    fun me(@RequestHeader("Authorization", required = false) auth: String?): ResponseEntity<Any> {
        val token = auth?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
            ?.substring(BEARER_PREFIX.length)?.trim()
            ?: return ResponseEntity.status(401).body(ErrorResponse("Missing bearer token"))

        // For /me we try every provider — the token may have been issued by any of them.
        val user = authProviders.firstNotNullOfOrNull { it.getUserInfo(token) }
            ?: return ResponseEntity.status(401).body(ErrorResponse("Invalid or expired token"))
        return ResponseEntity.ok(user)
    }

    private fun providerFor(type: AuthType): AuthProvider? =
        authProviders.firstOrNull { it.supports(type) }

    private fun handle(result: AuthResult): ResponseEntity<Any> = when (result) {
        is AuthResult.Success -> ResponseEntity.ok(LoginResponse(result.token, result.user))
        is AuthResult.Failure -> {
            log.debug("Authentication rejected: {}", result.reason)
            ResponseEntity.status(401).body(ErrorResponse(result.reason))
        }
        is AuthResult.MfaRequired -> ResponseEntity.status(401)
            .body(MfaRequiredResponse(result.challengeId))
    }

    private fun serviceUnavailable(message: String): ResponseEntity<Any> =
        ResponseEntity.status(503).body(ErrorResponse(message))

    companion object {
        private const val BEARER_PREFIX = "Bearer "
    }
}

data class LoginRequest(val username: String, val password: String)
data class RefreshRequest(val refreshToken: String)
data class LoginResponse(val token: TokenPair, val user: UserInfo)
data class ErrorResponse(val error: String)
data class MfaRequiredResponse(val mfaChallengeId: String, val error: String = "MFA required")
