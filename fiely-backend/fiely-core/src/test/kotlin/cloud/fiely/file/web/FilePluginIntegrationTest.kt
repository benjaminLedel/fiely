package cloud.fiely.file.web

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ConditionEvaluationResult
import org.junit.jupiter.api.extension.ExecutionCondition
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.sql.DataSource

/**
 * End-to-end test of the file pipeline — exercises the real stack:
 *  - Postgres 16 via Testcontainers
 *  - `fiely-auth-jwt` and `fiely-storage-local` plugins loaded as real JARs by
 *    PF4J from the `copyTestPlugins`-built directory
 *  - Core migrations V1..V4 (tenants + files)
 *  - A real JWT issued by `/api/auth/login`, then used to drive the
 *    `/api/files` endpoints end-to-end
 *  - Blobs materialised on the filesystem under a @TempDir
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@Testcontainers
@ExtendWith(FilePluginIntegrationTest.RequireDocker::class)
class FilePluginIntegrationTest {

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
            // V4 files table FKs auth_users so clear files first.
            c.prepareStatement("DELETE FROM files").execute()
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
    fun `login, upload, list, download, delete - end to end`() {
        val token = login()
        val payload = "fiely end-to-end test content".toByteArray()

        // Upload
        val uploadRes = mockMvc.multipart("/api/files") {
            file(MockMultipartFile("file", "greeting.txt", "text/plain", payload))
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("greeting.txt") }
            jsonPath("$.sizeBytes") { value(payload.size) }
        }.andReturn()
        val fileId = objectMapper.readTree(uploadRes.response.contentAsString)["id"].asText()

        // Blob exists on disk under the storage root.
        val blobsUnderRoot = Files.walk(blobRoot).use { s ->
            s.filter { Files.isRegularFile(it) }.count()
        }
        assert(blobsUnderRoot >= 1) { "expected at least one blob under $blobRoot, found $blobsUnderRoot" }

        // List
        mockMvc.get("/api/files") {
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].id") { value(fileId) }
        }

        // Download (StreamingResponseBody needs an asyncDispatch)
        val asyncResult = mockMvc.get("/api/files/$fileId/content") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isOk() } }.andReturn()
        mockMvc.perform(asyncDispatch(asyncResult))
            .andExpect(status().isOk)
            .andExpect(content().bytes(payload))

        // Delete
        mockMvc.delete("/api/files/$fileId") {
            header("Authorization", "Bearer $token")
        }.andExpect { status { isNoContent() } }

        val afterDelete = Files.walk(blobRoot).use { s ->
            s.filter { Files.isRegularFile(it) }.count()
        }
        assert(afterDelete == 0L) { "expected all blobs pruned after delete, found $afterDelete" }
    }

    @Test
    fun `unauthenticated requests are rejected`() {
        mockMvc.get("/api/files").andExpect { status { isUnauthorized() } }
    }

    private fun login(): String {
        val response = mockMvc.post("/api/auth/login") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"$USERNAME","password":"$PASSWORD"}"""
        }.andExpect {
            status { isOk() }
        }.andReturn()
        return objectMapper.readTree(response.response.contentAsString)["token"]["accessToken"].asText()
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

        @TempDir
        @JvmStatic
        lateinit var blobRoot: Path

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }

            val pluginsDir = System.getProperty("fiely.test.plugins.dir")
                ?: error(
                    "System property `fiely.test.plugins.dir` is not set — " +
                        "run tests via Gradle so the `copyTestPlugins` task prepares it.",
                )
            registry.add("fiely.plugins.dir") { pluginsDir }

            registry.add("fiely.plugins.fiely-auth-jwt.secret") {
                "integration-test-secret-must-be-at-least-32-chars"
            }
            registry.add("fiely.plugins.fiely-auth-jwt.issuer") { "fiely-test" }
            registry.add("fiely.plugins.fiely-storage-local.root") { blobRoot.toString() }
        }

        /** Matches `PasswordHasher.hash` in the jwt plugin — see note in AuthPluginIntegrationTest. */
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
