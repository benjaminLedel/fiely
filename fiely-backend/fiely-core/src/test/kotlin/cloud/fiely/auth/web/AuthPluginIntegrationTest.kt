package cloud.fiely.auth.web

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.testcontainers.DockerClientFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.sql.DataSource

/**
 * End-to-end integration test for the auth pipeline. Unlike
 * [AuthControllerTest] (which injects a stub [AuthProvider][cloud.fiely.plugin.AuthProvider]),
 * this test exercises the real stack:
 *
 *  - A Postgres 16 container started via Testcontainers.
 *  - The `fiely-auth-jwt` plugin packaged as a real JAR by the
 *    `copyTestPlugins` Gradle task and loaded by PF4J from `fiely.plugins.dir`.
 *  - `FielyPluginManager` running the plugin's `V1__auth_users.sql` migration
 *    against the container's Postgres in its dedicated
 *    `flyway_plugin_fiely_auth_jwt_history` table.
 *  - `DatabaseAuthProvider` authenticating against `auth_users` and issuing
 *    real HS256 JWTs that are then verified via `/api/auth/me`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@Testcontainers
@ExtendWith(AuthPluginIntegrationTest.RequireDocker::class)
class AuthPluginIntegrationTest {

    /**
     * Skip the whole class when Docker isn't available (e.g. on a dev laptop
     * without the daemon running). Evaluated before the Testcontainers and
     * SpringBootTest extensions, so no container start is attempted.
     */
    class RequireDocker : ExecutionCondition {
        override fun evaluateExecutionCondition(context: ExtensionContext): ConditionEvaluationResult =
            if (DockerClientFactory.instance().isDockerAvailable) {
                ConditionEvaluationResult.enabled("Docker available")
            } else {
                ConditionEvaluationResult.disabled(
                    "Docker is not available — skipping end-to-end integration test.",
                )
            }
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var dataSource: DataSource

    @BeforeEach
    fun seedUser() {
        val hash = encodePbkdf2(PASSWORD, iterations = 1_000)
        dataSource.connection.use { c ->
            c.prepareStatement("DELETE FROM auth_users").execute()
            c.prepareStatement(
                """
                INSERT INTO auth_users
                  (id, username, email, display_name, password_hash, roles, is_enabled)
                VALUES
                  (?::uuid, ?, ?, ?, ?, ?, TRUE)
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, UUID.randomUUID().toString())
                stmt.setString(2, USERNAME)
                stmt.setString(3, "alice@example.com")
                stmt.setString(4, "Alice")
                stmt.setString(5, hash)
                stmt.setArray(6, c.createArrayOf("text", arrayOf("user")))
                stmt.executeUpdate()
            }
        }
    }

    @Test
    fun `login with valid credentials issues a working JWT end-to-end`() {
        val loginResponse = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"$USERNAME","password":"$PASSWORD"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.user.username") { value(USERNAME) }
            jsonPath("$.user.email") { value("alice@example.com") }
            jsonPath("$.token.accessToken") { exists() }
            jsonPath("$.token.refreshToken") { exists() }
            jsonPath("$.token.expiresInSeconds") { value(3600) }
        }.andReturn()

        val body = objectMapper.readTree(loginResponse.response.contentAsString)
        val accessToken = body["token"]["accessToken"].asText()
        val refreshToken = body["token"]["refreshToken"].asText()

        // The token should round-trip: /me recognises it and returns the user.
        mockMvc.get("/api/auth/me") {
            header("Authorization", "Bearer $accessToken")
        }.andExpect {
            status { isOk() }
            jsonPath("$.username") { value(USERNAME) }
        }

        // Refresh should work too — and return a fresh access token.
        mockMvc.post("/api/auth/refresh") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"refreshToken":"$refreshToken"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.user.username") { value(USERNAME) }
            jsonPath("$.token.accessToken") { exists() }
        }
    }

    @Test
    fun `login with wrong password is rejected`() {
        mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"$USERNAME","password":"not-the-password"}"""
        }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.error") { value("Invalid credentials") }
        }
    }

    @Test
    fun `me rejects a token signed with a different secret`() {
        // A well-formed JWT from a different issuer/secret should still fail.
        val foreign = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJhdHRhY2tlciJ9.invalid"
        mockMvc.get("/api/auth/me") {
            header("Authorization", "Bearer $foreign")
        }.andExpect {
            status { isUnauthorized() }
        }
    }

    companion object {
        private const val USERNAME = "alice"
        private const val PASSWORD = "hunter2"

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("fiely_test")
            .withUsername("fiely")
            .withPassword("fiely")

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            // Database: point Spring at the Testcontainers Postgres.
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }

            // Plugins: point FielyPluginManager at the directory the Gradle
            // `copyTestPlugins` task populated with the real plugin JAR.
            val pluginsDir = System.getProperty("fiely.test.plugins.dir")
                ?: error(
                    "System property `fiely.test.plugins.dir` is not set — " +
                        "run tests via Gradle so the `copyTestPlugins` task prepares it.",
                )
            registry.add("fiely.plugins.dir") { pluginsDir }

            // Auth-plugin configuration — mirrors what a production deployment
            // would set via FIELY_JWT_SECRET. Must be at least 32 chars.
            registry.add("fiely.plugins.fiely-auth-jwt.secret") {
                "integration-test-secret-must-be-at-least-32-chars"
            }
            registry.add("fiely.plugins.fiely-auth-jwt.issuer") { "fiely-test" }
        }

        /**
         * Inline mirror of `PasswordHasher.hash()` from the fiely-auth-jwt
         * plugin. We don't depend on the plugin module from this test because
         * doing so would put the plugin's `META-INF/extensions.idx` on the
         * system classloader and PF4J would auto-register its extensions
         * outside the plugin sandbox. The plugin is loaded as a real JAR via
         * `copyTestPlugins`; this helper is only used to seed a test user.
         */
        private fun encodePbkdf2(password: String, iterations: Int): String {
            val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
            val hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            val b64 = Base64.getEncoder()
            return "pbkdf2\$sha256\$$iterations\$${b64.encodeToString(salt)}\$${b64.encodeToString(hash)}"
        }
    }
}
