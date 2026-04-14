package cloud.fiely.plugin

import org.pf4j.ExtensionPoint
import java.io.InputStream

/**
 * Extension point for pluggable file storage backends.
 *
 * The default implementation (fiely-storage-local) stores files on the local filesystem.
 */
interface StorageProvider : ExtensionPoint {
    val id: String

    fun store(path: StoragePath, data: InputStream, size: Long): FileReference
    fun retrieve(ref: FileReference): InputStream
    fun delete(ref: FileReference)
    fun exists(ref: FileReference): Boolean
    fun getSize(ref: FileReference): Long
}

data class StoragePath(
    val tenantId: String,
    val userId: String,
    val fileId: String,
    val version: Int
)

data class FileReference(
    val storageId: String,
    val path: String
)
