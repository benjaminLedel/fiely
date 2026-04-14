package cloud.fiely.auth.web

import cloud.fiely.plugin.AuthProvider
import cloud.fiely.plugin.AuthRequest
import cloud.fiely.plugin.AuthResult
import cloud.fiely.plugin.AuthType
import cloud.fiely.plugin.TokenPair
import cloud.fiely.plugin.UserInfo
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AuthControllerTest.StubProviderConfig::class)
class AuthControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var stub: StubAuthProvider

    @Test
    fun `login returns 200 with token pair and user info on valid credentials`() {
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                LoginRequest("alice", "hunter2"),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.token.accessToken") { value("access-token-for-alice") }
            jsonPath("$.token.refreshToken") { value("refresh-token-for-alice") }
            jsonPath("$.token.expiresInSeconds") { value(60) }
            jsonPath("$.user.id") { value("user-1") }
            jsonPath("$.user.username") { value("alice") }
        }
    }

    @Test
    fun `login returns 401 with error message on bad credentials`() {
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                LoginRequest("alice", "wrong-password"),
            )
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("Invalid credentials") }
        }
    }

    @Test
    fun `me returns 401 without bearer token`() {
        mockMvc.get("/api/auth/me").andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("Missing bearer token") }
        }
    }

    @Test
    fun `me returns user info for valid access token`() {
        mockMvc.get("/api/auth/me") {
            header("Authorization", "Bearer access-token-for-alice")
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value("user-1") }
            jsonPath("$.username") { value("alice") }
        }
    }

    @Test
    fun `me returns 401 for unknown token`() {
        mockMvc.get("/api/auth/me") {
            header("Authorization", "Bearer garbage")
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("Invalid or expired token") }
        }
    }

    @Test
    fun `refresh returns new token pair for valid refresh token`() {
        val before = stub.refreshCalls
        mockMvc.post("/api/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RefreshRequest("refresh-token-for-alice"),
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.token.accessToken") { value("access-token-for-alice") }
            jsonPath("$.user.username") { value("alice") }
        }
        // Confirm the provider was invoked exactly once by this call.
        assert(stub.refreshCalls == before + 1) {
            "Expected refresh call count to grow by 1, got ${stub.refreshCalls - before}"
        }
    }

    @Test
    fun `refresh returns 401 for invalid refresh token`() {
        mockMvc.post("/api/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(RefreshRequest("nope"))
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("Invalid refresh token") }
        }
    }

    // --- Test fixtures ---

    @TestConfiguration
    class StubProviderConfig {
        @Bean
        fun stubAuthProvider(): StubAuthProvider = StubAuthProvider()
    }

    /**
     * In-memory [AuthProvider] that knows exactly one user ("alice" / "hunter2").
     * We use a hand-written stub rather than the real `fiely-auth-jwt` plugin here
     * because loading a plugin JAR in a test would require packaging and a
     * plugins directory — orthogonal to what this controller test verifies.
     */
    class StubAuthProvider : AuthProvider {
        override val id: String = "stub"
        override val displayName: String = "Test Stub"

        var refreshCalls = 0
            private set

        private val user = UserInfo(
            id = "user-1",
            username = "alice",
            email = "alice@example.com",
            displayName = "Alice",
            roles = setOf("user"),
        )
        private val tokens = TokenPair(
            accessToken = "access-token-for-alice",
            refreshToken = "refresh-token-for-alice",
            expiresInSeconds = 60,
        )

        override fun supports(type: AuthType): Boolean = type == AuthType.USERNAME_PASSWORD

        override fun authenticate(request: AuthRequest): AuthResult {
            val username = request.credentials["username"]
            val password = request.credentials["password"]
            return if (username == "alice" && password == "hunter2") {
                AuthResult.Success(tokens, user)
            } else {
                AuthResult.Failure("Invalid credentials")
            }
        }

        override fun getUserInfo(token: String): UserInfo? =
            if (token == tokens.accessToken) user else null

        override fun refresh(refreshToken: String): AuthResult? {
            refreshCalls++
            return if (refreshToken == tokens.refreshToken) {
                AuthResult.Success(tokens, user)
            } else {
                null
            }
        }
    }
}
