package cloud.fiely.auth.web

import cloud.fiely.plugin.AuthProvider
import cloud.fiely.plugin.AuthRequest
import cloud.fiely.plugin.AuthResult
import cloud.fiely.plugin.AuthType
import cloud.fiely.plugin.TokenPair
import cloud.fiely.plugin.UserInfo
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.security.SecurityRequirements
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
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
 *
 * Providers are resolved through an [ObjectProvider] so we always see the
 * current set of beans — including those that PF4J's `ExtensionsInjector`
 * registers late, after `FielyPluginManager.init()` has run. Capturing
 * `List<AuthProvider>` at construction time would race with plugin
 * registration when controller bean creation happens first.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(
    name = "Auth",
    description = "Authentication endpoints delegating to the active AuthProvider plugin.",
)
class AuthController(private val authProviders: ObjectProvider<AuthProvider>) {

    private val log = LoggerFactory.getLogger(AuthController::class.java)

    @PostMapping("/login")
    @Operation(
        summary = "Exchange credentials for a token pair",
        description = "Forwards username/password to the first AuthProvider that supports " +
            "USERNAME_PASSWORD. Returns access + refresh tokens on success.",
    )
    @SecurityRequirements // public endpoint
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Authentication succeeded"),
            ApiResponse(responseCode = "401", description = "Invalid credentials or MFA required"),
            ApiResponse(responseCode = "503", description = "No auth provider is available"),
        ],
    )
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
    @Operation(
        summary = "Refresh an access token",
        description = "Trades a valid refresh token for a new access/refresh token pair.",
    )
    @SecurityRequirements // public endpoint — the refresh token lives in the request body
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Refresh succeeded"),
            ApiResponse(responseCode = "401", description = "Invalid or expired refresh token"),
            ApiResponse(responseCode = "503", description = "No auth provider is available"),
        ],
    )
    fun refresh(@RequestBody body: RefreshRequest): ResponseEntity<Any> {
        val provider = providerFor(AuthType.USERNAME_PASSWORD)
            ?: return serviceUnavailable("No auth provider available")

        val result = provider.refresh(body.refreshToken)
            ?: return ResponseEntity.status(401).body(ErrorResponse("Invalid refresh token"))
        return handle(result)
    }

    @GetMapping("/me")
    @Operation(
        summary = "Resolve the current user from a bearer token",
        description = "Iterates all registered AuthProviders and returns the user info for " +
            "the first one that recognises the token.",
        security = [SecurityRequirement(name = "bearerAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Token resolved to a user"),
            ApiResponse(responseCode = "401", description = "Missing, invalid or expired bearer token"),
        ],
    )
    fun me(@RequestHeader("Authorization", required = false) auth: String?): ResponseEntity<Any> {
        val token = auth?.takeIf { it.startsWith(BEARER_PREFIX, ignoreCase = true) }
            ?.substring(BEARER_PREFIX.length)?.trim()
            ?: return ResponseEntity.status(401).body(ErrorResponse("Missing bearer token"))

        // For /me we try every provider — the token may have been issued by any of them.
        val user = currentProviders().firstNotNullOfOrNull { it.getUserInfo(token) }
            ?: return ResponseEntity.status(401).body(ErrorResponse("Invalid or expired token"))
        return ResponseEntity.ok(user)
    }

    private fun providerFor(type: AuthType): AuthProvider? =
        currentProviders().firstOrNull { it.supports(type) }

    /** Snapshot of all currently-registered providers (PF4J + plain Spring beans). */
    private fun currentProviders(): List<AuthProvider> = authProviders.stream().toList()

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
