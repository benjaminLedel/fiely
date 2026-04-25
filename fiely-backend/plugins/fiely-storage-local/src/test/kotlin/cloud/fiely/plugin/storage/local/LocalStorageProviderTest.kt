package cloud.fiely.plugin.storage.local

import cloud.fiely.plugin.PluginServices
import cloud.fiely.plugin.StoragePath
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Exercises [LocalStorageProvider] directly (no PF4J, no Spring). A per-test
 * temp dir becomes the blob root. [PluginServices] is the production config
 * entry point so we initialise it with a temp root here.
 */
class LocalStorageProviderTest {

    @TempDir
    lateinit var tempRoot: Path

    private lateinit var provider: LocalStorageProvider

    @BeforeEach
    fun init() {
        PluginServices.initialise(
            // A DataSource is required by the API but unused by this provider.
            dataSource = NoopDataSource,
            config = mapOf("fiely.plugins.fiely-storage-local.root" to tempRoot.toString()),
        )
        provider = LocalStorageProvider()
    }

    @Test
    fun `store writes bytes under a sharded path and retrieve returns them verbatim`() {
        val payload = "hello fiely".toByteArray()
        val path = StoragePath(
            tenantId = "tenant-a",
            userId = "user-1",
            fileId = "abcdef1234567890",
            version = 1,
        )

        val ref = provider.store(path, payload.inputStream(), payload.size.toLong())
        assertEquals("fiely-storage-local", ref.storageId)

        // Sharded layout: <root>/tenant-a/ab/cd/abcdef1234567890/v1
        val expected = tempRoot.resolve("tenant-a/ab/cd/abcdef1234567890/v1")
        assertTrue(Files.exists(expected), "expected blob at $expected")
        assertArrayEquals(payload, Files.readAllBytes(expected))

        val retrieved = provider.retrieve(ref).use { it.readAllBytes() }
        assertArrayEquals(payload, retrieved)
        assertEquals(payload.size.toLong(), provider.getSize(ref))
        assertTrue(provider.exists(ref))
    }

    @Test
    fun `delete removes the blob and prunes empty parent directories`() {
        val payload = byteArrayOf(1, 2, 3, 4)
        val path = StoragePath("t", "u", "deadbeef00000000", 1)
        val ref = provider.store(path, payload.inputStream(), payload.size.toLong())

        provider.delete(ref)
        assertFalse(provider.exists(ref))

        // Parent dirs up to the tenant should have been pruned. The tenant
        // dir itself is empty so it goes too; the root stays.
        val tenantDir = tempRoot.resolve("t")
        assertFalse(Files.exists(tenantDir), "empty tenant dir should be pruned")
        assertTrue(Files.exists(tempRoot), "root dir must remain")
    }

    @Test
    fun `path-escape attempts are rejected`() {
        val badPath = StoragePath("t", "u", "../etc", 1)
        assertThrows(IllegalArgumentException::class.java) {
            provider.store(badPath, "oops".byteInputStream(), 4)
        }
    }

    @Test
    fun `partial write leaves no visible blob`() {
        val path = StoragePath("t", "u", "11223344aabbccdd", 1)
        val exploding = object : java.io.InputStream() {
            var read = 0
            override fun read(): Int {
                if (read++ > 3) throw java.io.IOException("boom")
                return 0x41
            }
        }
        assertThrows(java.io.IOException::class.java) {
            provider.store(path, exploding, 1024)
        }
        val target = tempRoot.resolve("t/11/22/11223344aabbccdd/v1")
        assertFalse(Files.exists(target), "partial writes must not materialise at the final path")
    }
}

/** A trivial DataSource stand-in — LocalStorageProvider never touches the DB. */
private object NoopDataSource : javax.sql.DataSource {
    override fun getConnection(): java.sql.Connection = error("unused in test")
    override fun getConnection(u: String?, p: String?): java.sql.Connection = error("unused in test")
    override fun getLogWriter(): java.io.PrintWriter? = null
    override fun setLogWriter(out: java.io.PrintWriter?) {}
    override fun setLoginTimeout(seconds: Int) {}
    override fun getLoginTimeout(): Int = 0
    override fun getParentLogger(): java.util.logging.Logger = java.util.logging.Logger.getGlobal()
    override fun <T : Any?> unwrap(iface: Class<T>?): T = error("unused")
    override fun isWrapperFor(iface: Class<*>?): Boolean = false
}
