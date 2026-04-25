package cloud.fiely.file.web

import cloud.fiely.auth.web.AuthenticatedUser
import cloud.fiely.file.domain.FileMetadataRepository
import cloud.fiely.file.domain.FileRepository
import cloud.fiely.plugin.UserInfo
import cloud.fiely.tenant.domain.TenantEntity
import cloud.fiely.tenant.domain.TenantRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.put
import java.util.UUID

/**
 * Exercises [FileMetadataController] end-to-end via H2 + stubbed
 * AuthProvider/StorageProvider. Reuses the same fixtures as
 * [FileControllerTest] via `@Import`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FileControllerTest.FileTestConfig::class)
class FileMetadataControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var tenants: TenantRepository
    @Autowired private lateinit var files: FileRepository
    @Autowired private lateinit var metadata: FileMetadataRepository
    @Autowired private lateinit var storage: FileControllerTest.InMemoryStorage
    @Autowired private lateinit var resolverStub: FileControllerTest.StubCurrentUserResolver

    private val userId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val tenantId = TenantEntity.DEFAULT_ID

    @BeforeEach
    fun seed() {
        storage.clear()
        metadata.deleteAll()
        files.deleteAll()
        tenants.deleteAll()
        tenants.save(
            TenantEntity(
                id = tenantId,
                slug = "default",
                name = "Default",
                maxUploadBytes = 4096,
            ),
        )
        resolverStub.set(
            AuthenticatedUser(
                info = UserInfo(
                    id = userId.toString(),
                    username = "alice",
                    email = null,
                    displayName = null,
                ),
                userId = userId,
                tenantId = tenantId,
            ),
        )
    }

    @Test
    fun `put, get, list, delete round-trip`() {
        val fileId = uploadFile()

        // PUT user-namespace metadata
        mockMvc.put("/api/files/$fileId/metadata/user") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"tags":["draft","resume"],"rating":5}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.namespace") { value("user") }
            jsonPath("$.data.tags[0]") { value("draft") }
            jsonPath("$.data.rating") { value(5) }
        }

        // GET single namespace
        mockMvc.get("/api/files/$fileId/metadata/user").andExpect {
            status { isOk() }
            jsonPath("$.data.rating") { value(5) }
        }

        // Upsert replaces the document wholesale.
        mockMvc.put("/api/files/$fileId/metadata/user") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"tags":["final"]}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.tags[0]") { value("final") }
            jsonPath("$.data.rating") { doesNotExist() }
        }

        // Second namespace coexists.
        mockMvc.put("/api/files/$fileId/metadata/exif") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"camera":"Pixel 9","iso":100}"""
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/files/$fileId/metadata").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
        }

        // DELETE just the user namespace — exif survives.
        mockMvc.delete("/api/files/$fileId/metadata/user").andExpect {
            status { isNoContent() }
        }
        mockMvc.get("/api/files/$fileId/metadata").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].namespace") { value("exif") }
        }
    }

    @Test
    fun `metadata is cascade-deleted with the file`() {
        val fileId = uploadFile()
        mockMvc.put("/api/files/$fileId/metadata/user") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"tags":["x"]}"""
        }.andExpect { status { isOk() } }

        mockMvc.delete("/api/files/$fileId").andExpect { status { isNoContent() } }

        // Reaching into the repo because the HTTP endpoint for the file now
        // 404s and would shadow a cascade-failure bug.
        val remaining = metadata.findAllByIdFileId(UUID.fromString(fileId))
        assert(remaining.isEmpty()) { "metadata should cascade-delete with its file, found $remaining" }
    }

    @Test
    fun `unauthenticated requests are rejected`() {
        val fileId = uploadFile()
        resolverStub.set(null)
        mockMvc.get("/api/files/$fileId/metadata").andExpect { status { isUnauthorized() } }
        mockMvc.put("/api/files/$fileId/metadata/user") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `put on a file owned by someone else returns 404`() {
        val fileId = uploadFile()
        resolverStub.set(
            AuthenticatedUser(
                info = UserInfo(id = UUID.randomUUID().toString(), username = "bob", email = null, displayName = null),
                userId = UUID.randomUUID(),
                tenantId = tenantId,
            ),
        )
        mockMvc.put("/api/files/$fileId/metadata/user") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"x":1}"""
        }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `invalid namespace is rejected`() {
        val fileId = uploadFile()
        mockMvc.put("/api/files/$fileId/metadata/has spaces") {
            contentType = MediaType.APPLICATION_JSON
            content = "{}"
        }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `get on a missing namespace returns 404`() {
        val fileId = uploadFile()
        mockMvc.get("/api/files/$fileId/metadata/ghost").andExpect { status { isNotFound() } }
    }

    // Small helper — uploads a one-byte file and returns its id.
    private fun uploadFile(): String {
        val res = mockMvc.multipart("/api/files") {
            file(MockMultipartFile("file", "note.txt", "text/plain", "x".toByteArray()))
        }.andReturn()
        return objectMapper.readTree(res.response.contentAsString)["id"].asText()
    }
}
