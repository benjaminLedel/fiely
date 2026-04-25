package cloud.fiely.plugin.storage.local

import cloud.fiely.plugin.FileReference
import cloud.fiely.plugin.PluginServices
import cloud.fiely.plugin.StoragePath
import cloud.fiely.plugin.StorageProvider
import org.pf4j.Extension
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

/**
 * Filesystem-backed [StorageProvider]. Blobs are stored under a configurable
 * root directory using a UUID-sharded layout:
 *
 *     <root>/<tenantId>/<xx>/<yy>/<fileId>/v<N>
 *
 * where `xx`/`yy` are the first two hex pairs of `fileId`. The sharding
 * avoids giant flat directories on common filesystems and aligns 1:1 with
 * the [StoragePath] contract.
 *
 * Durability: [store] writes to a sibling `.tmp` file first and then renames
 * with ATOMIC_MOVE (falling back to plain move) so a crash mid-write never
 * leaves a half-written blob visible under its final path.
 *
 * Stream ownership: [retrieve] returns a fresh [InputStream] that the caller
 * owns and must close. The provider keeps no state that would be disturbed by
 * the caller holding the stream for an arbitrary amount of time.
 */
@Extension
class LocalStorageProvider : StorageProvider {

    private val log = LoggerFactory.getLogger(LocalStorageProvider::class.java)

    override val id: String = "fiely-storage-local"

    private val root: Path by lazy { resolveRoot() }

    override fun store(path: StoragePath, data: InputStream, size: Long): FileReference {
        val target = resolve(path)
        Files.createDirectories(target.parent)

        val tmp = target.resolveSibling(target.fileName.toString() + ".tmp")
        try {
            Files.newOutputStream(tmp).use { out ->
                data.copyTo(out)
            }
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: UnsupportedOperationException) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Throwable) {
            runCatching { Files.deleteIfExists(tmp) }
            throw e
        }

        val relative = root.relativize(target).toString().replace('\\', '/')
        log.debug("Stored blob at {}", target)
        return FileReference(storageId = id, path = relative)
    }

    override fun retrieve(ref: FileReference): InputStream {
        val target = root.resolve(ref.path).normalize()
        requireUnderRoot(target)
        return Files.newInputStream(target)
    }

    override fun delete(ref: FileReference) {
        val target = root.resolve(ref.path).normalize()
        requireUnderRoot(target)
        Files.deleteIfExists(target)

        // Best-effort prune of now-empty parent directories (up to the tenant
        // root). Swallow errors — a non-empty directory or permissions issue
        // is not a delete failure.
        var dir: Path? = target.parent
        while (dir != null && dir != root && dir.startsWith(root)) {
            try {
                Files.delete(dir)
            } catch (_: Throwable) {
                break
            }
            dir = dir.parent
        }
    }

    override fun exists(ref: FileReference): Boolean {
        val target = root.resolve(ref.path).normalize()
        if (!target.startsWith(root)) return false
        return Files.exists(target)
    }

    override fun getSize(ref: FileReference): Long {
        val target = root.resolve(ref.path).normalize()
        requireUnderRoot(target)
        return Files.size(target)
    }

    // --- Internals ----------------------------------------------------------

    private fun resolveRoot(): Path {
        val configured = PluginServices.configFor("fiely-storage-local")["root"] as? String
        val path = Paths.get(configured ?: "./data/blobs").toAbsolutePath().normalize()
        Files.createDirectories(path)
        log.info("LocalStorageProvider root: {}", path)
        return path
    }

    private fun resolve(path: StoragePath): Path {
        val fileId = path.fileId
        val shard1 = fileId.take(2)
        val shard2 = fileId.drop(2).take(2)
        return root
            .resolve(sanitise(path.tenantId))
            .resolve(shard1)
            .resolve(shard2)
            .resolve(sanitise(fileId))
            .resolve("v${path.version}")
            .normalize()
    }

    private fun requireUnderRoot(candidate: Path) {
        if (!candidate.startsWith(root)) {
            throw SecurityException("Resolved blob path escapes the storage root")
        }
    }

    /** Defensive: prevent `..`/`/` sneaking into path segments via injected ids. */
    private fun sanitise(segment: String): String {
        if (segment.contains('/') || segment.contains('\\') || segment == ".." || segment == ".") {
            throw IllegalArgumentException("Illegal path segment: $segment")
        }
        return segment
    }
}
