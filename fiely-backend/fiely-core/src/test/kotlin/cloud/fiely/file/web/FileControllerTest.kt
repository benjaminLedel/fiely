package cloud.fiely.file.web

import cloud.fiely.auth.web.AuthenticatedUser
import cloud.fiely.auth.web.CurrentUserResolver
import cloud.fiely.file.domain.FileRepository
import cloud.fiely.plugin.FileReference
import cloud.fiely.plugin.StoragePath
import cloud.fiely.plugin.StorageProvider
import cloud.fiely.plugin.UserInfo
import cloud.fiely.tenant.domain.TenantEntity
import cloud.fiely.tenant.domain.TenantRepository
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.multipart
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Exercises the [FileController] + [cloud.fiely.file.service.FileService] pair
 * against in-memory Postgres-compatible H2.
 *
 * We stub [CurrentUserResolver] to dodge the `auth_users` lookup — this test
 * covers file-tree semantics, not auth. The end-to-end plumbing (real plugin,
 * real token) lives in [cloud.fiely.file.web.FilePluginIntegrationTest].
 *
 * An in-memory [StorageProvider] stands in for the real plugin.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FileControllerTest.FileTestConfig::class)
class FileControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var tenants: TenantRepository
    @Autowired private lateinit var files: FileRepository
    @Autowired private lateinit var storage: InMemoryStorage
    @Autowired private lateinit var resolverStub: StubCurrentUserResolver

    private val userId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val tenantId = TenantEntity.DEFAULT_ID

    @BeforeEach
    fun seed() {
        storage.clear()
        files.deleteAll()
        tenants.deleteAll()
        tenants.save(
            TenantEntity(
                id = tenantId,
                slug = "default",
                name = "Default",
                maxUploadBytes = 1024, // small so we can test 413 cheaply
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
    fun `upload, list, download, delete round-trip`() {
        val payload = "hello fiely".toByteArray()
        val uploaded = mockMvc.multipart("/api/files") {
            file(MockMultipartFile("file", "hello.txt", "text/plain", payload))
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("hello.txt") }
            jsonPath("$.isFolder") { value(false) }
            jsonPath("$.sizeBytes") { value(payload.size) }
            jsonPath("$.contentType") { value("text/plain") }
        }.andReturn()
        val id = objectMapper.readTree(uploaded.response.contentAsString)["id"].asText()

        mockMvc.get("/api/files").andExpect {
            status { isOk() }
            jsonPath("$[0].name") { value("hello.txt") }
        }

        // StreamingResponseBody is async — need an asyncDispatch to drain it.
        val asyncResult = mockMvc.get("/api/files/$id/content").andExpect {
            status { isOk() }
            header { string("Content-Disposition", "attachment; filename*=UTF-8''hello.txt") }
        }.andReturn()
        mockMvc.perform(asyncDispatch(asyncResult))
            .andExpect(status().isOk)
            .andExpect(content().bytes(payload))

        mockMvc.delete("/api/files/$id").andExpect { status { isNoContent() } }
        mockMvc.get("/api/files").andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(0) }
        }
    }

    @Test
    fun `upload without a bearer token is rejected`() {
        resolverStub.set(null)
        mockMvc.multipart("/api/files") {
            file(MockMultipartFile("file", "x.txt", "text/plain", "x".toByteArray()))
        }.andExpect { status { isUnauthorized() } }
    }

    @Test
    fun `upload over tenant limit returns 413`() {
        val big = ByteArray(2048) { 'a'.code.toByte() } // > 1024 limit
        mockMvc.multipart("/api/files") {
            file(MockMultipartFile("file", "big.bin", "application/octet-stream", big))
        }.andExpect {
            status { isPayloadTooLarge() }
            jsonPath("$.error") { value(org.hamcrest.Matchers.containsString("exceeds tenant limit")) }
        }
    }

    @Test
    fun `upload with duplicate name in same parent returns 409`() {
        val payload = "a".toByteArray()
        mockMvc.multipart("/api/files") {
            file(MockMultipartFile("file", "dup.txt", "text/plain", payload))
        }.andExpect { status { isOk() } }
        mockMvc.multipart("/api/files") {
            file(MockMultipartFile("file", "dup.txt", "text/plain", payload))
        }.andExpect { status { isConflict() } }
    }

    @Test
    fun `folder create and nested upload and recursive delete`() {
        val folder = mockMvc.post("/api/files/folder") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"docs"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.isFolder") { value(true) }
            jsonPath("$.name") { value("docs") }
        }.andReturn()
        val folderId = objectMapper.readTree(folder.response.contentAsString)["id"].asText()

        val payload = "content".toByteArray()
        mockMvc.multipart("/api/files") {
            file(MockMultipartFile("file", "inside.txt", "text/plain", payload))
            param("parentId", folderId)
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/files") { param("parentId", folderId) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].name") { value("inside.txt") }
        }

        // Delete the folder — the service must ask the StorageProvider to
        // drop the nested blob too.
        val blobsBefore = storage.size
        mockMvc.delete("/api/files/$folderId").andExpect { status { isNoContent() } }
        assert(storage.size == blobsBefore - 1) { "expected nested blob to be deleted from storage" }
    }

    @Test
    fun `rename and move via PATCH`() {
        val upload = mockMvc.multipart("/api/files") {
            file(MockMultipartFile("file", "old.txt", "text/plain", "a".toByteArray()))
        }.andReturn()
        val fileId = objectMapper.readTree(upload.response.contentAsString)["id"].asText()

        val folder = mockMvc.post("/api/files/folder") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"target"}"""
        }.andReturn()
        val folderId = objectMapper.readTree(folder.response.contentAsString)["id"].asText()

        mockMvc.patch("/api/files/$fileId") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"new.txt","parentId":"$folderId"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.name") { value("new.txt") }
            jsonPath("$.parentId") { value(folderId) }
        }
    }

    @Test
    fun `moving a folder into its own descendant is rejected`() {
        val outer = create("outer")
        val inner = create("inner", parentId = outer)
        mockMvc.patch("/api/files/$outer") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"parentId":"$inner"}"""
        }.andExpect { status { isBadRequest() } }
    }

    private fun create(name: String, parentId: String? = null): String {
        val body = if (parentId != null) """{"name":"$name","parentId":"$parentId"}""" else """{"name":"$name"}"""
        val r = mockMvc.post("/api/files/folder") {
            contentType = MediaType.APPLICATION_JSON
            content = body
        }.andReturn()
        return objectMapper.readTree(r.response.contentAsString)["id"].asText()
    }

    // --- Fixtures -----------------------------------------------------------

    @TestConfiguration
    class FileTestConfig {
        @Bean
        fun inMemoryStorage(): InMemoryStorage = InMemoryStorage()

        @Bean
        @Primary
        fun stubCurrentUserResolver(): StubCurrentUserResolver = StubCurrentUserResolver()
    }

    /**
     * Minimal in-memory StorageProvider. Registered as a Spring bean so the
     * FileService picks it up via `ObjectProvider<StorageProvider>`.
     */
    class InMemoryStorage : StorageProvider {
        override val id: String = "fiely-storage-local"
        private val blobs = ConcurrentHashMap<String, ByteArray>()

        val size: Int get() = blobs.size
        fun clear() = blobs.clear()

        override fun store(path: StoragePath, data: InputStream, size: Long): FileReference {
            val key = "${path.tenantId}/${path.fileId}/v${path.version}"
            blobs[key] = data.readAllBytes()
            return FileReference(storageId = id, path = key)
        }

        override fun retrieve(ref: FileReference): InputStream =
            (blobs[ref.path] ?: error("blob not found: ${ref.path}")).inputStream()

        override fun delete(ref: FileReference) { blobs.remove(ref.path) }
        override fun exists(ref: FileReference): Boolean = blobs.containsKey(ref.path)
        override fun getSize(ref: FileReference): Long = blobs[ref.path]?.size?.toLong() ?: -1
    }

    /** Stand-in for [CurrentUserResolver] that returns a pre-set principal. */
    class StubCurrentUserResolver : CurrentUserResolver(
        authProviders = emptyObjectProvider(),
        jdbc = org.springframework.jdbc.core.JdbcTemplate(),
    ) {
        @Volatile
        private var principal: AuthenticatedUser? = null
        fun set(p: AuthenticatedUser?) { principal = p }
        override fun resolve(request: HttpServletRequest): AuthenticatedUser? = principal

        companion object {
            private fun emptyObjectProvider(): org.springframework.beans.factory.ObjectProvider<cloud.fiely.plugin.AuthProvider> =
                object : org.springframework.beans.factory.ObjectProvider<cloud.fiely.plugin.AuthProvider> {
                    override fun getObject(vararg args: Any?) = error("unused")
                    override fun getObject() = error("unused")
                    override fun getIfAvailable() = null
                    override fun getIfUnique() = null
                    override fun stream(): java.util.stream.Stream<cloud.fiely.plugin.AuthProvider> = java.util.stream.Stream.empty()
                }
        }
    }
}
